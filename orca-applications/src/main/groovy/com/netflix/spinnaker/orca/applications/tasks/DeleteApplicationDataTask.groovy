package com.netflix.spinnaker.orca.applications.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.MortService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.front50.model.Application;
import com.netflix.spinnaker.orca.front50.tasks.AbstractFront50Task;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import groovy.util.logging.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import retrofit.RetrofitError;

@Slf4j
@Component
class DeleteApplicationDataTask implements Task {

  @Autowired
  OortService oortService

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    def application = objectMapper.convertValue(stage.context.application, Application)
    def applicationName = application.name as String

    try {
      def finished = oortService.deleteAppData(applicationName)
      if (finished) {
        return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
      }

      return TaskResult.builder(ExecutionStatus.TERMINAL).context([exception: [
        details: [
          error: "Application has outstanding dependencies",
          errors: "应用数据清理时失败"
        ]
      ]]).build()
    } catch (RetrofitError e) {
      if (!e.response) {
        def exception = [operation: stage.tasks[-1].name, reason: e.message]
        return TaskResult.builder(ExecutionStatus.TERMINAL).context([exception: exception]).build()
      } else if (e.response && e.response.status && e.response.status != 404) {
        def resp = e.response
        def exception = [statusCode: resp.status, operation: stage.tasks[-1].name, url: resp.url, reason: resp.reason]
        try {
          exception.details = e.getBodyAs(Map) as Map
        } catch (ignored) {
        }
        return TaskResult.builder(ExecutionStatus.TERMINAL).context([exception: exception]).build()
      }
    }
  }

}
