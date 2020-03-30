package com.netflix.spinnaker.orca.echo.pipeline;

import com.netflix.spinnaker.orca.echo.tasks.CreateTcrTriggerTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * this module only use for CD
 *
 * @author wangwei CD-Group
 * @date 2020/03/25 1:49 下午
 */
@Component
public class CreateTcrTriggerStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("createTcrTrigger", CreateTcrTriggerTask.class);
  }
}
