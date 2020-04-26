package com.netflix.spinnaker.orca.echo.pipeline;

import com.netflix.spinnaker.orca.echo.tasks.DeleteTcrTriggerTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * this module only use for CD
 *
 * @author wangwei CD-Group
 * @date 2020/04/23 4:49 下午
 */
@Component
public class DeleteTcrTriggerStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("deleteTcrTrigger", DeleteTcrTriggerTask.class);
  }
}
