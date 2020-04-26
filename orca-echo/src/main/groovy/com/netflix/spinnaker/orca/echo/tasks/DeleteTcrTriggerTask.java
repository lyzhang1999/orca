/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo.tasks;

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;

import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.echo.EchoService;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import groovy.transform.CompileStatic;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * this module only use for CD
 *
 * @author wangwei CD-Group
 * @date 2020/04/23 4:49 下午
 */
@Component
@CompileStatic
public class DeleteTcrTriggerTask implements RetryableTask {
  @Autowired EchoService echoService;

  @Autowired(required = false)
  private Front50Service front50Service;

  private Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(30);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(5);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    log.info("start delete Trc Trigger");
    String userGK = stage.getExecution().getAuthentication().getUser();
    log.info("TCR TRIGGER USER IS {}", userGK);
    byte[] pipelineData;
    try {
      pipelineData = Base64.getDecoder().decode((String) stage.getContext().get("pipeline"));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("pipeline must be encoded as base64", e);
    }
    log.info("Expanded encoded pipeline:" + new String(pipelineData));

    Map<String, Object> pipeline = (Map<String, Object>) stage.decodeBase64("/pipeline", Map.class);
    try {
      Map<String, Object> existingPipeline = fetchExistingPipeline(pipeline);
      log.info("------------------- existingPipeline is ------------------ {}", existingPipeline);
      getAndDeleteOldPipelineTcrTrigger(userGK, existingPipeline);
    } catch (Exception e) {
      log.info("fetchExistingPipeline pipeline Error {}", e);
    }
    return TaskResult.ofStatus(SUCCEEDED);
  }

  private Map<String, Object> fetchExistingPipeline(Map<String, Object> newPipeline) {
    String applicationName = (String) newPipeline.get("application");
    String newPipelineID = (String) newPipeline.get("id");
    if (!StringUtils.isEmpty(newPipelineID)) {
      return front50Service.getPipelines(applicationName).stream()
          .filter(m -> m.containsKey("id"))
          .filter(m -> m.get("id").equals(newPipelineID))
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  void getAndDeleteOldPipelineTcrTrigger(String userGK, Map<String, Object> existingPipeline) {
    try {
      List<HashMap> oldTrigger = (List<HashMap>) existingPipeline.get("triggers");
      List<EchoService.TcrTrigger> needDelete = new ArrayList<>();
      for (HashMap trigger : oldTrigger) {
        if (!"tcr_webhook".equalsIgnoreCase(String.valueOf(trigger.get("type")))) continue;
        if (trigger.get("tcrRegistryId") == null || trigger.get("tcrRegionName") == null) return;
        EchoService.TcrTrigger tcrTrigger = new EchoService.TcrTrigger();
        String tcrTriggerName = trigger.get("source").toString();
        tcrTrigger.setRegion(trigger.get("tcrRegionName").toString());
        tcrTrigger.setInstanceId(trigger.get("tcrRegistryId").toString());
        List<Map<String, Object>> tcrReturnTrigger = getTcrTriggerList(userGK, tcrTrigger);
        List<Map<String, Object>> result =
            tcrReturnTrigger.stream()
                .filter(n -> tcrTriggerName.equals(String.valueOf(n.get("name"))))
                .filter(n -> String.valueOf(n.get("description")).indexOf("CODING DevOps") > -1)
                .collect(Collectors.toList());
        if (result.size() != 0) {
          Map<String, Object> resultRow = result.get(0);
          tcrTrigger.setTriggerId((Integer) resultRow.get("id"));
          tcrTrigger.setNamespaceId((Integer) resultRow.get("namespaceId"));
          needDelete.add(tcrTrigger);
        } else {
          log.error("can not find name {} in tcr trigger", tcrTriggerName);
        }
      }
      log.info("need delete TCR Trigger is {}", needDelete);
      if (needDelete.size() != 0) deleteTcrTrigger(userGK, needDelete);
    } catch (Exception e) {
      log.error("getAndDeleteOldPipelineTcrTrigger error {}", e);
    }
  }

  List<Map<String, Object>> getTcrTriggerList(String userGK, EchoService.TcrTrigger trigger) {
    log.info(
        "-------- trigger.getRegion() {} trigger.getInstance() {}",
        trigger.getRegion(),
        trigger.getInstanceId());
    List<Map<String, Object>> response =
        echoService.getTcrTriggerList(userGK, trigger.getRegion(), trigger.getInstanceId());
    return response;
  }

  void deleteTcrTrigger(String userGK, List<EchoService.TcrTrigger> trigger) {
    log.info("------------- deleteTcrTrigger --------------");
    trigger.forEach(
        n -> {
          Integer response =
              echoService
                  .deleteTcrTrigger(
                      userGK,
                      n.getRegion(),
                      n.getInstanceId(),
                      n.getNamespaceId(),
                      n.getTriggerId())
                  .getStatus();
          if (response.equals(200)) {
            log.info("delete tcr trigger name {} success", n.getTriggerId());
          } else {
            log.info("delete tcr trigger name {} fail", n.getTriggerId());
          }
        });
  }
}
