/**
 * Copyright 2017 StreamSets Inc.
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
package com.streamsets.datacollector.store;

import com.streamsets.datacollector.restapi.bean.UserJson;
import com.streamsets.datacollector.task.Task;
import com.streamsets.datacollector.util.PipelineException;
import com.streamsets.lib.security.acl.dto.Acl;
import com.streamsets.lib.security.acl.dto.Action;
import com.streamsets.lib.security.acl.dto.ResourceType;

import java.util.Set;

public interface AclStoreTask extends Task {
  Acl createAcl(
      String name,
      ResourceType resourceType,
      long resourceCreateTime,
      String resourceOwner
  ) throws PipelineStoreException;

  Acl saveAcl(String name, Acl acl) throws PipelineException;

  Acl getAcl(String name) throws PipelineException;

  void deleteAcl(String name);

  void validateReadPermission(String pipelineName, UserJson currentUser) throws PipelineException;

  void validateWritePermission(String pipelineName, UserJson currentUser) throws PipelineException;

  void validateExecutePermission(String pipelineName, UserJson currentUser) throws PipelineException;

  boolean isPermissionGranted(String pipelineName, Set<Action> actions, UserJson currentUser) throws PipelineException;
}
