package com.netflix.spinnaker.orca.echo.pipeline;

import com.netflix.spinnaker.orca.echo.tasks.CreateCcrTriggerTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * this module only use for CD
 *
 * @author wangwei CD-Group
 * @date 2020/05/26 6:20 下午
 */
@Component
public class CreateCcrTriggerStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("syncCcrTrigger", CreateCcrTriggerTask.class);
  }
}
