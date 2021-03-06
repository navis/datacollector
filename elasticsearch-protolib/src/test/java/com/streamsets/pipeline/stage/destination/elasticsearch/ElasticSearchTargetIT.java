/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.destination.elasticsearch;

import com.google.common.collect.ImmutableMap;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.api.el.ELVars;
import com.streamsets.pipeline.lib.el.RecordEL;
import com.streamsets.pipeline.sdk.RecordCreator;
import com.streamsets.pipeline.sdk.TargetRunner;
import com.streamsets.testing.NetworkUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalSettingsPreparer;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public class ElasticSearchTargetIT {
  private static String esName = UUID.randomUUID().toString();
  private static Node esServer;
  private static int esHttpPort;

  @BeforeClass
  public static void setUp() throws Exception {
    File esDir = new File("target", UUID.randomUUID().toString());
    esHttpPort = NetworkUtils.getRandomPort();
    Assert.assertTrue(esDir.mkdirs());
    Map<String, Object> configs = new HashMap<>();
    configs.put("cluster.name", esName);
    configs.put("http.enabled", true);
    configs.put("http.port", esHttpPort);
    configs.put("path.home", esDir.getAbsolutePath());
    configs.put("path.conf", esDir.getAbsolutePath());
    configs.put("path.data", esDir.getAbsolutePath());
    configs.put("path.logs", esDir.getAbsolutePath());
    esServer = createTestNode(configs);
    esServer.start();
  }

  @AfterClass
  public static void cleanUp() throws Exception {
    if (esServer != null) {
      esServer.close();
    }
  }

  // this is needed in embedded mode.
  private static void prepareElasticSearchServerForQueries() {
    esServer.client().admin().indices().prepareRefresh().execute().actionGet();
  }

  private static Node createTestNode(Map<String, Object> configs) {
    Settings.Builder settings = Settings.builder();
    for (Map.Entry<String, Object> config : configs.entrySet()) {
      settings.put(config.getKey(), config.getValue());
    }
    return new TestNode(settings.build());
  }

  @SuppressWarnings("unchecked")
  private static class TestNode extends Node {
    TestNode(Settings settings) {
      super(
          InternalSettingsPreparer.prepareEnvironment(settings, null),
          // To enable an http port in integration tests, the following plugin must be loaded.
          (Collection) Arrays.asList(Netty4Plugin.class)
      );
    }
  }

  @Test
  public void testValidations() throws Exception {
    ElasticSearchConfigBean conf = new ElasticSearchConfigBean();
    conf.httpUris = Collections.EMPTY_LIST;
    conf.timeDriver = "${time:now()}";
    conf.timeZoneID = "UTC";
    conf.indexTemplate = "${record:value('/index')x}";
    conf.typeTemplate = "${record:nonExistentFunction()}";
    conf.docIdTemplate = "";
    conf.charset = "UTF-8";
    conf.defaultOperation = ElasticSearchOperationType.INDEX;
    conf.useSecurity= false;
    conf.securityConfigBean = new SecurityConfigBean();

    Target target = new ElasticSearchTarget(conf);
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    List<Stage.ConfigIssue> issues = runner.runValidateConfigs();
    Assert.assertEquals(3, issues.size());
    Assert.assertTrue(issues.get(0).toString().contains(Errors.ELASTICSEARCH_00.name()));
    Assert.assertTrue(issues.get(1).toString().contains(Errors.ELASTICSEARCH_03.name()));
    Assert.assertTrue(issues.get(2).toString().contains(Errors.ELASTICSEARCH_06.name()));

    conf.httpUris = Arrays.asList("x");
    conf.timeDriver = "${time:now()}";
    conf.timeZoneID = "UTC";
    conf.indexTemplate = "x";
    conf.typeTemplate = "x";
    conf.docIdTemplate = "";
    conf.charset = "UTF-8";
    conf.defaultOperation = ElasticSearchOperationType.INDEX;
    conf.useSecurity = false;
    conf.securityConfigBean = new SecurityConfigBean();

    target = new ElasticSearchTarget(conf);
    runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    issues = runner.runValidateConfigs();
    Assert.assertEquals(1, issues.size());
    Assert.assertTrue(issues.get(0).toString().contains(Errors.ELASTICSEARCH_07.name()));

    conf.httpUris = Arrays.asList("localhost:0");
    conf.timeDriver = "${time:now()}";
    conf.timeZoneID = "UTC";
    conf.indexTemplate = "x";
    conf.typeTemplate = "x";
    conf.docIdTemplate = "";
    conf.charset = "UTF-8";
    conf.defaultOperation = ElasticSearchOperationType.INDEX;
    conf.useSecurity = false;
    conf.securityConfigBean = new SecurityConfigBean();

    target = new ElasticSearchTarget(conf);
    runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    issues = runner.runValidateConfigs();
    Assert.assertEquals(1, issues.size());
    Assert.assertTrue(issues.get(0).toString().contains(Errors.ELASTICSEARCH_09.name()));
  }

  private Target createTarget() {
    return createTarget(
        "${time:now()}",
        "${record:value('/index')}",
        "",
        ElasticSearchOperationType.INDEX
    );
  }

  private ElasticSearchTarget createTarget(String timeDriver, String indexEL, String docIdEL, ElasticSearchOperationType op) {
    ElasticSearchConfigBean conf = new ElasticSearchConfigBean();
    conf.httpUris = Arrays.asList("127.0.0.1:" + esHttpPort);
    conf.timeDriver = timeDriver;
    conf.timeZoneID = "UTC";
    conf.indexTemplate = indexEL;
    conf.typeTemplate = "${record:value('/type')}";
    conf.docIdTemplate = docIdEL;
    conf.charset = "UTF-8";
    conf.defaultOperation = op;
    conf.useSecurity = false;
    conf.securityConfigBean = new SecurityConfigBean();

    return new ElasticSearchTarget(conf);
  }

  @Test
  public void testWriteRecords() throws Exception {
    Target target = createTarget();
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    try {
      runner.runInit();
      List<Record> records = new ArrayList<>();
      Record record1 = RecordCreator.create();
      record1.set(Field.create(ImmutableMap.of("a", Field.create("Hello"),
                                               "index", Field.create("i"), "type", Field.create("t"))));
      Record record2 = RecordCreator.create();
      record2.set(Field.create(ImmutableMap.of("a", Field.create("Bye"),
                                               "index", Field.create("i"), "type", Field.create("t"))));
      records.add(record1);
      records.add(record2);
      runner.runWrite(records);
      Assert.assertTrue(runner.getErrorRecords().isEmpty());
      Assert.assertTrue(runner.getErrors().isEmpty());


      prepareElasticSearchServerForQueries();

      Set<Map> expected = new HashSet<>();
      expected.add(ImmutableMap.of("a", "Hello", "index", "i", "type", "t"));
      expected.add(ImmutableMap.of("a", "Bye", "index", "i", "type", "t"));

      SearchResponse response = esServer.client().prepareSearch("i").setTypes("t")
                                        .setSearchType(SearchType.DEFAULT).execute().actionGet();
      SearchHit[] hits = response.getHits().getHits();
      Assert.assertEquals(2, hits.length);
      Set<Map> got = new HashSet<>();
      got.add(hits[0].getSource());
      got.add(hits[1].getSource());

      Assert.assertEquals(expected, got);

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWriteRecordsOnErrorDiscard() throws Exception {
    Target target = createTarget();
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).setOnRecordError(OnRecordError.DISCARD).build();
    try {
      runner.runInit();
      List<Record> records = new ArrayList<>();
      Record record1 = RecordCreator.create();
      record1.set(Field.create(ImmutableMap.of("a", Field.create("Hello"),
                                               "index", Field.create("II"), "type", Field.create("t"))));
      Record record2 = RecordCreator.create();
      record2.set(Field.create(ImmutableMap.of("a", Field.create("Bye"),
                                               "index", Field.create("ii"), "type", Field.create("t"))));
      records.add(record1);
      records.add(record2);
      runner.runWrite(records);
      Assert.assertTrue(runner.getErrorRecords().isEmpty());
      Assert.assertTrue(runner.getErrors().isEmpty());


      prepareElasticSearchServerForQueries();

      Set<Map> expected = new HashSet<>();
      expected.add(ImmutableMap.of("a", "Bye", "index", "ii", "type", "t"));

      SearchResponse response = esServer.client().prepareSearch("ii").setTypes("t")
                                        .setSearchType(SearchType.DEFAULT).execute().actionGet();
      SearchHit[] hits = response.getHits().getHits();
      Assert.assertEquals(1, hits.length);
      Set<Map> got = new HashSet<>();
      got.add(hits[0].getSource());

      Assert.assertEquals(expected, got);

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWriteRecordsOnErrorToError() throws Exception {
    Target target = createTarget();
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).setOnRecordError(OnRecordError.TO_ERROR).build();
    try {
      runner.runInit();
      List<Record> records = new ArrayList<>();
      Record record1 = RecordCreator.create();
      record1.set(Field.create(ImmutableMap.of("a", Field.create("Hello"),
                                               "index", Field.create("III"), "type", Field.create("t"))));
      Record record2 = RecordCreator.create();
      record2.set(Field.create(ImmutableMap.of("a", Field.create("Bye"),
                                               "index", Field.create("iii"), "type", Field.create("t"))));
      records.add(record1);
      records.add(record2);
      runner.runWrite(records);
      Assert.assertEquals(1, runner.getErrorRecords().size());
      Assert.assertEquals("Hello", runner.getErrorRecords().get(0).get("/a").getValueAsString());
      Assert.assertTrue(runner.getErrors().isEmpty());


      prepareElasticSearchServerForQueries();

      Set<Map> expected = new HashSet<>();
      expected.add(ImmutableMap.of("a", "Bye", "index", "iii", "type", "t"));

      SearchResponse response = esServer.client().prepareSearch("iii").setTypes("t")
                                        .setSearchType(SearchType.DEFAULT).execute().actionGet();
      SearchHit[] hits = response.getHits().getHits();
      Assert.assertEquals(1, hits.length);
      Set<Map> got = new HashSet<>();
      got.add(hits[0].getSource());

      Assert.assertEquals(expected, got);

    } finally {
      runner.runDestroy();
    }
  }

  @Test(expected = StageException.class)
  public void testWriteRecordsOnErrorStopPipeline() throws Exception {
    Target target = createTarget();
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).setOnRecordError(OnRecordError.STOP_PIPELINE).build();
    try {
      runner.runInit();
      List<Record> records = new ArrayList<>();
      Record record1 = RecordCreator.create();
      record1.set(Field.create(ImmutableMap.of("a", Field.create("Hello"),
                                               "index", Field.create("IIII"), "type", Field.create("t"))));
      Record record2 = RecordCreator.create();
      record2.set(Field.create(ImmutableMap.of("a", Field.create("Bye"),
                                               "index", Field.create("iiii"), "type", Field.create("t"))));
      records.add(record1);
      records.add(record2);
      runner.runWrite(records);
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testTimeDriverNow() throws Exception {
    ElasticSearchConfigBean conf = new ElasticSearchConfigBean();
    conf.httpUris = Arrays.asList("127.0.0.1:" + esHttpPort);
    conf.timeDriver = "${time:now()}";
    conf.timeZoneID = "UTC";
    conf.indexTemplate = "${YYYY()}";
    conf.typeTemplate = "${record:value('/type')}";
    conf.docIdTemplate = "";
    conf.charset = "UTF-8";
    conf.defaultOperation = ElasticSearchOperationType.INDEX;
    conf.useSecurity = false;
    conf.securityConfigBean = new SecurityConfigBean();

    ElasticSearchTarget target = new ElasticSearchTarget(conf);
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    runner.runInit();
    try {
      Record record = RecordCreator.create();
      Date timeNow = target.setBatchTime();
      Date timeGot = target.getRecordTime(record);
      Assert.assertEquals(timeNow, timeGot);
      ELVars elVars = runner.getContext().createELVars();
      RecordEL.setRecordInContext(elVars, record);
      String index = target.getRecordIndex(elVars, record);
      Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
      calendar.setTime(timeNow);
      Assert.assertEquals(Integer.toString(calendar.get(Calendar.YEAR)), index);
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testTimeDriverValue() throws Exception {
    ElasticSearchTarget target = createTarget(
        "${record:value('/')}",
        "${YYYY()}",
        "",
        ElasticSearchOperationType.INDEX
    );
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    runner.runInit();
    try {
      Record record = RecordCreator.create();
      Date timeNow = target.setBatchTime();
      record.set(Field.createDatetime(timeNow));
      Thread.sleep(10);
      target.setBatchTime();
      Date timeGot = target.getRecordTime(record);
      Assert.assertEquals(timeNow, timeGot);
      ELVars elVars = runner.getContext().createELVars();
      RecordEL.setRecordInContext(elVars, record);
      String index = target.getRecordIndex(elVars, record);
      Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
      calendar.setTime(timeNow);
      Assert.assertEquals(Integer.toString(calendar.get(Calendar.YEAR)), index);
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWriteRecordsNow() throws Exception {
    ElasticSearchTarget target = createTarget(
        "${time:now()}",
        "${YYYY()}",
        "",
        ElasticSearchOperationType.INDEX
    );
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    try {
      runner.runInit();
      List<Record> records = new ArrayList<>();
      Record record1 = RecordCreator.create();
      record1.set(Field.create(ImmutableMap.of("a", Field.create("Hello"),
          "type", Field.create("t"))));
      Record record2 = RecordCreator.create();
      record2.set(Field.create(ImmutableMap.of("a", Field.create("Bye"),
          "type", Field.create("t"))));
      records.add(record1);
      records.add(record2);
      runner.runWrite(records);
      Assert.assertTrue(runner.getErrorRecords().isEmpty());
      Assert.assertTrue(runner.getErrors().isEmpty());

      Date batchTime = target.getBatchTime();
      Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
      calendar.setTime(batchTime);
      String index = Integer.toString(calendar.get(Calendar.YEAR));

      prepareElasticSearchServerForQueries();

      Set<Map> expected = new HashSet<>();
      expected.add(ImmutableMap.of("a", "Hello", "type", "t"));
      expected.add(ImmutableMap.of("a", "Bye",  "type", "t"));

      SearchResponse response = esServer.client().prepareSearch(index).setTypes("t")
          .setSearchType(SearchType.DEFAULT).execute().actionGet();
      SearchHit[] hits = response.getHits().getHits();
      Assert.assertEquals(2, hits.length);
      Set<Map> got = new HashSet<>();
      got.add(hits[0].getSource());
      got.add(hits[1].getSource());

      Assert.assertEquals(expected, got);

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testInvalidUrisAndSecurityUser() throws Exception {
    ElasticSearchConfigBean conf = new ElasticSearchConfigBean();
    conf.timeDriver = "${time:now()}";
    conf.timeZoneID = "UTC";
    conf.indexTemplate = "${YYYY()}";
    conf.typeTemplate = "${record:value('/type')}";
    conf.docIdTemplate = "";
    conf.charset = "UTF-8";
    conf.defaultOperation = ElasticSearchOperationType.INDEX;
    conf.useSecurity = false;
    conf.securityConfigBean = new SecurityConfigBean();

    // Invalid url
    conf.httpUris = Arrays.asList("127.0.0.1:" + "NOT_A_NUMBER");

    ElasticSearchTarget target = new ElasticSearchTarget(conf);
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    List<Stage.ConfigIssue> issues = runner.runValidateConfigs();
    Assert.assertEquals(1, issues.size());
    Assert.assertTrue(issues.get(0).toString().contains(Errors.ELASTICSEARCH_07.name()));

    // Invalid port number
    conf.httpUris = Arrays.asList("127.0.0.1:" + Integer.MAX_VALUE);

    target = new ElasticSearchTarget(conf);
    runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    issues = runner.runValidateConfigs();
    Assert.assertEquals(1, issues.size());
    Assert.assertTrue(issues.get(0).toString().contains(Errors.ELASTICSEARCH_08.name()));

    // Invalid shield user
    conf.httpUris = Arrays.asList("127.0.0.1:" + esHttpPort);
    conf.useSecurity = true;
    conf.securityConfigBean.securityUser = "INVALID_SHIELD_USER";

    target = new ElasticSearchTarget(conf);
    runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    issues = runner.runValidateConfigs();
    Assert.assertEquals(1, issues.size());
    Assert.assertTrue(issues.get(0).toString().contains(Errors.ELASTICSEARCH_20.name()));
  }

  @Test
  public void testNonIndexOperationWithoutDocId() throws Exception {
    ElasticSearchConfigBean conf = new ElasticSearchConfigBean();
    conf.httpUris = Arrays.asList("127.0.0.1:" + esHttpPort);
    conf.timeDriver = "${time:now()}";
    conf.timeZoneID = "UTC";
    conf.indexTemplate = "${YYYY()}";
    conf.typeTemplate = "${record:value('/type')}";
    conf.docIdTemplate = ""; // empty document ID expression
    conf.charset = "UTF-8";
    conf.defaultOperation = ElasticSearchOperationType.CREATE;
    conf.useSecurity = false;
    conf.securityConfigBean = new SecurityConfigBean();

    ElasticSearchTarget target = new ElasticSearchTarget(conf);
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    List<Stage.ConfigIssue> issues = runner.runValidateConfigs();
    Assert.assertEquals(1, issues.size());
    Assert.assertTrue(issues.get(0).toString().contains(Errors.ELASTICSEARCH_19.name()));
  }

  @Test
  public void testUpsertRecords() throws Exception {
    // Use the index field as document ID.
    Target target = createTarget(
        "${time:now()}",
        "${record:value('/index')}",
        "docId",
        ElasticSearchOperationType.INDEX
    );
    TargetRunner runner = new TargetRunner.Builder(ElasticSearchDTarget.class, target).build();
    try {
      runner.runInit();
      List<Record> records = new ArrayList<>();
      Record record1 = RecordCreator.create();
      record1.set(Field.create(ImmutableMap.of("a", Field.create("Old"),
          "index", Field.create("j"), "type", Field.create("t"))));
      Record record2 = RecordCreator.create();
      record2.set(Field.create(ImmutableMap.of("a", Field.create("New"),
          "index", Field.create("j"), "type", Field.create("t"))));
      records.add(record1);
      records.add(record2);
      runner.runWrite(records);
      Assert.assertTrue(runner.getErrorRecords().isEmpty());
      Assert.assertTrue(runner.getErrors().isEmpty());

      prepareElasticSearchServerForQueries();

      // First record must be replaced by second record: "Old" => "New".
      Set<Map> expected = new HashSet<>();
      expected.add(ImmutableMap.of("a", "New", "index", "j", "type", "t"));

      SearchResponse response = esServer.client().prepareSearch("j").setTypes("t")
          .setSearchType(SearchType.DEFAULT).execute().actionGet();
      SearchHit[] hits = response.getHits().getHits();
      Assert.assertEquals(1, hits.length);
      Set<Map> got = new HashSet<>();
      got.add(hits[0].getSource());

      Assert.assertEquals(expected, got);

    } finally {
      runner.runDestroy();
    }
  }

}
