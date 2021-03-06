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

/**
 * Controller for Library Pane Share Modal.
 */

angular
  .module('dataCollectorApp.home')
  .controller('ShareModalInstanceController', function ($scope, $modalInstance, $translate, api, pipelineInfo) {
    angular.extend($scope, {
      pipelineInfo: pipelineInfo,
      common: {
        errors: []
      },
      loading:true,
      newSubjectList: {
        value: []
      },
      userList: [],
      groupList: [],

      save : function () {
        api.pipelineAgent.savePipelineAcl(pipelineInfo.name, $scope.acl)
          .then(
            function(res) {
              $modalInstance.close(res.data);
            },
            function(res) {
              $scope.common.errors = [res.data];
            }
          );
      },

      cancel : function () {
        $modalInstance.dismiss('cancel');
      },

      inviteOthers: function () {
        if ($scope.newSubjectList.value && $scope.newSubjectList.value.length) {
          angular.forEach($scope.newSubjectList.value, function(newSubject) {
            var subjectType = 'USER';
            if ($scope.groupList.indexOf(newSubject) !== -1) {
              subjectType = 'GROUP';
            }
            $scope.acl.permissions.push({
              resourceId: $scope.acl.resourceId,
              subjectId: newSubject,
              subjectType: subjectType,
              actions: ['READ', 'WRITE', 'EXECUTE']
            });
          });
          $scope.newSubjectList.value = [];
        }
      },

      onActionToggle: function (permission, action, $event) {
        var checkbox = $event.target;
        if (checkbox.checked && permission.actions.indexOf(action) === -1) {
          permission.actions.push(action);
        } else {
          permission.actions = _.filter(permission.actions, function(act) {
            return act !== action;
          });
        }
      },

      removePermission: function (permission, index) {
        $scope.acl.permissions.splice(index, 1);
      }
    });

    var alreadyAddedSubjects = [];
    var fetchAcl = function() {
      api.pipelineAgent.getPipelineConfigAcl(pipelineInfo.name)
        .then(
          function(res) {
            $scope.acl = res.data;
            if ($scope.acl && $scope.acl.permissions) {
              angular.forEach($scope.acl.permissions, function(permission) {
                alreadyAddedSubjects.push(permission.subjectId);
              });
            }

            fetchUsersAndGroups();
          },
          function(res) {
            $scope.common.errors = [res.data];
          }
        );
    };

    var fetchUsersAndGroups = function() {
      $scope.fetching = true;
      api.admin.getUsers()
        .then(
          function (res) {
            $scope.fetching = false;
            var users = _.sortBy(res.data, 'name');
            var userList = [];
            var groupList = [];
            angular.forEach(users, function(user) {
              if (alreadyAddedSubjects.indexOf(user.name) === -1) {
                userList.push(user.name);
              }
              if (user.groups && user.groups.length) {
                angular.forEach(user.groups, function (group) {
                  if (groupList.indexOf(group) === -1 && alreadyAddedSubjects.indexOf(group) === -1) {
                    groupList.push(group);
                  }
                });
              }
            });
            $scope.userList = userList;
            $scope.groupList = groupList;
          },
          function (res) {
            $rootScope.common.errors = [res.data];
          }
        );
    };

    fetchAcl();

  });
