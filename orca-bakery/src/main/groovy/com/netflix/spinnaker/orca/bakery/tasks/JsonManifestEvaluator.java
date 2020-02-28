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

package com.netflix.spinnaker.orca.bakery.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import io.micrometer.core.instrument.util.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
@Slf4j
@RequiredArgsConstructor
public class JsonManifestEvaluator {

  private final ArtifactResolver artifactResolver;
  private final OortService oort;
  private final ObjectMapper objectMapper;
  private final ContextParameterProcessor contextParameterProcessor;

  private final RetrySupport retrySupport = new RetrySupport();

  public String evaluate(Stage stage, BakeContext context) throws IOException {
    String rawManifest;
    Map<Object, Object> manifest = new HashMap<>();
    String manifestStr = "";

    if (BakeContext.Source.Artifact.equals(context.getSource())) {
      Artifact manifestArtifact =
          artifactResolver.getBoundArtifactForStage(
              stage, context.getManifestArtifactId(), context.getManifestArtifact());

      if (manifestArtifact == null) {
        throw new IllegalArgumentException("No manifest artifact was specified.");
      }

      if (manifestArtifact.getArtifactAccount() == null) {
        throw new IllegalArgumentException("No manifest artifact account was specified.");
      }

      log.info("Using {} as the manifest", manifestArtifact);

      rawManifest =
          retrySupport.retry(
              () -> {
                Response manifestText = oort.fetchArtifact(manifestArtifact);
                try (InputStream body = manifestText.getBody().in()) {
                  return IOUtils.toString(body, StandardCharsets.UTF_8);
                } catch (Exception e) {
                  log.warn("Failure fetching/parsing manifests from {}", manifestArtifact, e);
                  // forces a retry
                  throw new IllegalStateException(e);
                }
              },
              10,
              200,
              true); // retry 10x, starting at .2s intervals);

      log.info("read raw manifest: {}", rawManifest);

      Map<String, Object> unevaluatedManifest = objectMapper.readValue(rawManifest, Map.class);

      if (!unevaluatedManifest.isEmpty()) {
        Map<String, Object> manifestWrapper = new HashMap<>();
        manifestWrapper.put("manifests", unevaluatedManifest);

        if (!context.isSkipExpressionEvaluation()) {
          manifestWrapper =
              contextParameterProcessor.process(
                  manifestWrapper, contextParameterProcessor.buildExecutionContext(stage), true);

          if (manifestWrapper.containsKey("expressionEvaluationSummary")) {
            throw new IllegalStateException(
                "Failure evaluating manifest expressions: "
                    + manifestWrapper.get("expressionEvaluationSummary"));
          }
        }
        manifest = (Map<Object, Object>) manifestWrapper.get("manifests");
      }
    } else {
      manifest = context.getManifest();
    }

    // check local shell Provisioner 防止恶意脚本
    Optional.ofNullable(manifest.getOrDefault("provisioners", null))
        .ifPresent(
            it -> {
              List<Map> provisions = (List<Map>) it;
              for (Map provisioner : provisions) {
                String type = (String) provisioner.get("type");
                if (type.equals("shell-local")) {
                  log.info("packer 配置使用了 shell-local provisioner ");
                  throw new IllegalArgumentException("can not use shell-local provisioner");
                }
              }
            });
    manifestStr = objectMapper.writeValueAsString(manifest);
    log.info("json manifest evaluuator got result : {}", manifestStr);
    return manifestStr;
  }
}
