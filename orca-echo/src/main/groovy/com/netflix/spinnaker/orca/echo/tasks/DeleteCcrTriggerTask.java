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
 * @date 2020/05/26 6:15 下午
 */
@Component
@CompileStatic
public class DeleteCcrTriggerTask implements RetryableTask {
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
    log.info("start delete Crc Trigger");
    String userGK = stage.getExecution().getAuthentication().getUser();
    log.info("CCR TRIGGER USER IS {}", userGK);
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
      getAndDeleteOldPipelineCcrTrigger(userGK, existingPipeline);
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

  void getAndDeleteOldPipelineCcrTrigger(String userGK, Map<String, Object> existingPipeline) {
    try {
      List<HashMap> oldTrigger = (List<HashMap>) existingPipeline.get("triggers");
      List<EchoService.CcrTrigger> needDelete = new ArrayList<>();
      for (HashMap trigger : oldTrigger) {
        if (!"ccr_webhook".equalsIgnoreCase(String.valueOf(trigger.get("type")))) continue;
        if (trigger.get("ccrRepo") == null || trigger.get("ccrRegion") == null) continue;
        EchoService.CcrTrigger ccrTrigger = new EchoService.CcrTrigger();
        String ccrTriggerName = trigger.get("source").toString();
        ccrTrigger.setRegion(trigger.get("ccrRegion").toString());
        ccrTrigger.setRepoName(trigger.get("ccrRepo").toString());
        List<Map<String, Object>> ccrReturnTrigger = getCcrTriggerList(userGK, ccrTrigger);
        List<Map<String, Object>> result =
            ccrReturnTrigger.stream()
                .filter(n -> ccrTriggerName.equals(String.valueOf(n.get("name"))))
                .collect(Collectors.toList());
        if (result.size() != 0) {
          Map<String, Object> resultRow = result.get(0);
          ccrTrigger.setRepoName(trigger.get("ccrRepo").toString());
          ccrTrigger.setRegion(trigger.get("ccrRegion").toString());
          ccrTrigger.setTriggerName(resultRow.get("name").toString());
          needDelete.add(ccrTrigger);
        } else {
          log.error("can not find name {} in ccr trigger", ccrTriggerName);
        }
      }
      log.info("need delete CCR Trigger is {}", needDelete);
      if (needDelete.size() != 0) deleteCcrTrigger(userGK, needDelete);
    } catch (Exception e) {
      log.error("getAndDeleteOldPipelineCcrTrigger error {}", e);
    }
  }

  List<Map<String, Object>> getCcrTriggerList(String userGK, EchoService.CcrTrigger trigger) {
    log.info(
        "-------- trigger.getRegion() {} trigger.getInstance() {}",
        trigger.getRegion(),
        trigger.getRepoName());
    List<Map<String, Object>> response =
        echoService.getCcrTriggerList(
            userGK,
            trigger.getRegion(),
            new String(Base64.getEncoder().encode(trigger.getRepoName().getBytes())));
    return response;
  }

  void deleteCcrTrigger(String userGK, List<EchoService.CcrTrigger> trigger) {
    log.info("------------- deleteCcrTrigger --------------");
    trigger.forEach(
        n -> {
          Integer response =
              echoService.deleteCcrTrigger(userGK, n.getRegion(), n.getTriggerName()).getStatus();
          if (response.equals(200)) {
            log.info("delete ccr trigger name {} success", n.getTriggerName());
          } else {
            log.info("delete ccr trigger name {} fail", n.getTriggerName());
          }
        });
  }
}
