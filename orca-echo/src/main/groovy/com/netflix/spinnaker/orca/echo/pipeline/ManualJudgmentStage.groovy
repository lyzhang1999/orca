/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.grpc.client.CodingGrpcClient
import com.netflix.spinnaker.orca.grpc.client.ECodingGrpcClient
import net.coding.cd.proto.ApplicationProto
import net.coding.e.proto.TeamProto

import java.util.concurrent.TimeUnit
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.*
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import com.netfilx.spinnaker.orca.coding.common.TeamHelper

@Component
class ManualJudgmentStage implements StageDefinitionBuilder, AuthenticatedStage {

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("waitForJudgment", WaitForManualJudgmentTask.class)
  }

  @Override
  void prepareStageForRestart(Stage stage) {
    stage.context.remove("judgmentStatus")
    stage.context.remove("lastModifiedBy")
  }

  @Override
  Optional<User> authenticatedUser(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (stageData.state != StageData.State.CONTINUE || !stage.lastModified?.user || !stageData.propagateAuthenticationContext) {
      return Optional.empty()
    }

    def user = new User()
    user.setAllowedAccounts(stage.lastModified.allowedAccounts)
    user.setUsername(stage.lastModified.user)
    user.setEmail(stage.lastModified.user)
    return Optional.of(user.asImmutable())
  }

  @Slf4j
  @Component
  @VisibleForTesting
  public static class WaitForManualJudgmentTask implements OverridableTimeoutRetryableTask {
    final long backoffPeriod = 15000
    final long timeout = TimeUnit.DAYS.toMillis(3)

    @Autowired(required = false)
    EchoService echoService

    @Autowired(required = false)
    Front50Service front50Service

    @Autowired
    ObjectMapper objectMapper

    @Autowired
    CodingGrpcClient codingGrpcClient

    @Autowired
    ECodingGrpcClient ecodingGrpcClient

    @Override
    TaskResult execute(Stage stage) {
      StageData stageData = stage.mapTo(StageData)
      String notificationState
      ExecutionStatus executionStatus

      switch (stageData.state) {
        case StageData.State.CONTINUE:
          notificationState = "manualJudgmentContinue"
          executionStatus = ExecutionStatus.SUCCEEDED
          break
        case StageData.State.STOP:
          notificationState = "manualJudgmentStop"
          executionStatus = ExecutionStatus.TERMINAL
          break
        default:
          notificationState = "manualJudgment"
          executionStatus = ExecutionStatus.RUNNING
          break
      }

      Map outputs = processNotifications(stage, stageData, notificationState)
      // coding-cd 注入人工确认消息
      try {
        insertOrUpdateManualJudge(stage, stageData, notificationState)
      } catch(Exception e) {
        log.warn("insertOrUpdateManualJudge error {}", e)
      }

      return TaskResult.builder(executionStatus).context(outputs).build()
    }

    private static enum STATUS {
      RUNNING(0),
      SUCCEEDED(1),
      TERMINAL(2)
    }

    void insertOrUpdateManualJudge(Stage stage, StageData stageData, executionStatus) {
      /**
       * string stageId = stage.id
       string pipelineId = stage.execution.id
       string pipelineConfigId = stage.execution.pipelineConfigId
       string pipelineConfigName = stage.execution.name
       string application = stage.execution.application(team 前面);
       int32 teamId = stage.execution.application(team 后面);
       int64 createTime = stage.execution.startTime
       string linkUrl = 跳转到详情页的 URL;
       string userGKs = stage.execution.stages[0].context.codingJudgmentUsers -> {ArrayList@14239}.global_key
       string userAvatars = GRPC 取
       int32 status =  // 人工确认状态，0 待确认，1 已确认，2 取消   executionStatus 上面的注释
       * */
      String applicationName = TeamHelper.getTargetSuffix(stage.execution.application)
      Integer teamId = TeamHelper.getTeamId(stage.execution.application)
      Map<String, Object> manualJudge = new HashMap<>()
      manualJudge.put("stageId", stage.id)
      manualJudge.put("pipelineId", stage.execution.id)
      manualJudge.put("pipelineConfigId", stage.execution.pipelineConfigId)
      manualJudge.put("pipelineConfigName", stage.execution.name)
      manualJudge.put("application", applicationName)
      manualJudge.put("teamId", teamId)
      manualJudge.put("createTime", stage.execution.startTime)
      manualJudge.put("linkUrl", getProjectUrl(teamId, applicationName, stage.execution.id))
      manualJudge.put("userGKs", "")
      manualJudge.put("userAvatars", "")
      manualJudge.put("status", STATUS.valueOf(executionStatus))
    }

    String getProjectUrl(Integer teamId, String pureApplicationName, String executionId) {
      String applicationCloudProviders = "kubernetes"
      try {
        Application application = front50Service.get(TeamHelper.withSuffix(teamId, pureApplicationName))
        applicationCloudProviders = String.valueOf(application.cloudProviders) == "tencent" ? "tencent" : "kubernetes"
        log.debug("cloudProviders is {}", applicationCloudProviders)
      } catch (Exception e ) {
        log.warn("{} getProjectUrl error {}", TeamHelper.withSuffix(teamId, pureApplicationName), e)
      }
      String manualJudgeUrl = ""
      try {
        TeamProto.Team team = ecodingGrpcClient.getTeamByTeamId(teamId)
        if (team.getGlobalKey().equals("")) throw new Exception("teamGk ${teamId} can not find")
        String codingProjectUrl = "https://" + team.getGlobalKey() + ".coding.net"
        List<ApplicationProto.Application> applicationList = codingGrpcClient.getCodingApplication(team.getGlobalKey(), pureApplicationName)
        ApplicationProto.Application codingApplication = applicationList.stream().filter({ s -> s.getBindApplication().equals(true) }).findFirst().orElse(null)
        if (codingApplication == null) throw new Exception("spinnaker application not bind project, ignore")
        manualJudgeUrl = codingProjectUrl + "/p/" + codingApplication.getName() + "/cd-spin/delivery/" + applicationCloudProviders + "/flow/detail/" + executionId
      } catch(Exception e) {
        log.warn("Error {} get applicationList for {} {}", e.getMessage(), teamId, pureApplicationName)
      }
      if (manualJudgeUrl.equals("")) {
        return "#"
      }
      return manualJudgeUrl
    }

    Map processNotifications(Stage stage, StageData stageData, String notificationState) {
      if (echoService) {
        // sendNotifications will be true if using the new scheme for configuration notifications.
        // The new scheme matches the scheme used by the other stages.
        // If the deprecated scheme is in use, only the original 'awaiting judgment' notification is supported.
        if (notificationState != "manualJudgment" && !stage.context.sendNotifications) {
          return [:]
        }
        // get application type
        String cloudProviders = "kubernetes"
        try {
          Application application = front50Service.get(stage.execution.application)
          cloudProviders = String.valueOf(application.cloudProviders) == "tencent" ? "tencent" : "kubernetes"
          log.debug("application {} cloudProviders is {}", application, cloudProviders)
        } catch (Exception e ) {
          log.warn("manual judge get application error {}", stage.execution.application)
        }

        stageData.notifications.findAll { it.shouldNotify(notificationState) }.each {
          try {
            it.notify(echoService, stage, notificationState, cloudProviders)
          } catch (Exception e) {
            log.error("Unable to send notification (executionId: ${stage.execution.id}, address: ${it.address}, type: ${it.type})", e)
          }
        }

        return [notifications: stageData.notifications]
      } else {
        return [:]
      }
    }
  }

  static class StageData {
    String judgmentStatus = ""
    List<Notification> notifications = []
    boolean propagateAuthenticationContext

    State getState() {
      switch (judgmentStatus?.toLowerCase()) {
        case "continue":
          return State.CONTINUE
        case "stop":
          return State.STOP
        default:
          return State.UNKNOWN
      }
    }

    enum State {
      CONTINUE,
      STOP,
      UNKNOWN
    }
  }

  @Slf4j
  static class Notification {
    String address
    String cc
    String type
    String publisherName
    List<String> when
    Map<String, Map> message

    Map<String, Date> lastNotifiedByNotificationState = [:]
    Long notifyEveryMs = -1

    boolean shouldNotify(String notificationState, Date now = new Date()) {
      // The new scheme for configuring notifications requires the use of the when list (just like the other stages).
      // If this list is present, but does not contain an entry for this particular notification state, do not notify.
      if (when && !when.contains(notificationState)) {
        return false
      }

      Date lastNotified = lastNotifiedByNotificationState[notificationState]

      if (!lastNotified?.time) {
        return true
      }

      if (notifyEveryMs <= 0) {
        return false
      }

      return new Date(lastNotified.time + notifyEveryMs) <= now
    }

    void notify(EchoService echoService, Stage stage, String notificationState, String cloudProviders) {
      log.debug("cloudProviders is {}", cloudProviders)
      echoService.create(new EchoService.Notification(
        notificationType: EchoService.Notification.Type.valueOf(type.toUpperCase()),
        to: address ? [address] : (publisherName ? [publisherName] : null),
        cc: cc ? [cc] : null,
        templateGroup: notificationState,
        severity: EchoService.Notification.Severity.HIGH,
        source: new EchoService.Notification.Source(
          executionType: stage.execution.type.toString(),
          executionId: stage.execution.id,
          application: stage.execution.application,
          cloudProviders: cloudProviders
        ),
        additionalContext: [
          stageName: stage.name,
          stageId: stage.refId,
          restrictExecutionDuringTimeWindow: stage.context.restrictExecutionDuringTimeWindow,
          execution: stage.execution,
          instructions: stage.context.instructions ?: "",
          message: message?.get(notificationState)?.text,
          judgmentInputs: stage.context.judgmentInputs,
          judgmentInput: stage.context.judgmentInput,
          judgedBy: stage.context.lastModifiedBy
        ]
      ))
      lastNotifiedByNotificationState[notificationState] = new Date()
    }
  }
}
