/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.JobRunner;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.RunJobManifestContext;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import io.micrometer.core.instrument.util.IOUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.client.Response;

@Component
@Data
@Slf4j
public class KubernetesJobRunner implements JobRunner {
  private static final ThreadLocal<Yaml> yamlParser =
      ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor()));

  private static final String PREFIX_CUSTOM_ENV = "YAML_CONTENT";

  @Autowired OortService oort;

  private final RetrySupport retrySupport = new RetrySupport();

  private boolean katoResultExpected = false;
  private String cloudProvider = "kubernetes";

  private ArtifactResolver artifactResolver;
  private ObjectMapper objectMapper;
  private ManifestEvaluator manifestEvaluator;

  public KubernetesJobRunner(
      ArtifactResolver artifactResolver,
      ObjectMapper objectMapper,
      ManifestEvaluator manifestEvaluator) {
    this.artifactResolver = artifactResolver;
    this.objectMapper = objectMapper;
    this.manifestEvaluator = manifestEvaluator;
  }

  public List<Map> getOperations(Stage stage) {
    Map<String, Object> operation = new HashMap<>();

    if (stage.getContext().containsKey("cluster")) {
      operation.putAll((Map) stage.getContext().get("cluster"));
    } else {
      operation.putAll(stage.getContext());
    }

    RunJobManifestContext runJobManifestContext = stage.mapTo(RunJobManifestContext.class);
    if (runJobManifestContext.getSource().equals(ManifestContext.Source.Artifact)) {
      ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, runJobManifestContext);

      List<Map<Object, Object>> manifests = result.getManifests();
      if (manifests.size() != 1) {
        throw new IllegalArgumentException("Run Job only supports manifests with a single Job.");
      }

      operation.put("source", "text");
      operation.put("manifest", manifests.get(0));
      operation.put("requiredArtifacts", result.getRequiredArtifacts());
      operation.put("optionalArtifacts", result.getOptionalArtifacts());
    }

    try {
      replaceEnvArtifacts(stage);
    } catch (Exception e) {
      log.warn("replaceEnvArtifacts fail", e);
    }

    KubernetesContainerFinder.populateFromStage(operation, stage, artifactResolver);

    Map<String, Object> task = new HashMap<>();
    task.put(OPERATION, operation);
    return Collections.singletonList(task);
  }

  @SuppressWarnings("unchecked")
  private void replaceEnvArtifacts(Stage stage) {
    RunJobManifestContext runJobManifestContext = stage.mapTo(RunJobManifestContext.class);

    List<Map<String, Object>> containers =
        Optional.of(stage.getContext())
            .map(o -> (Map<String, Object>) o.get("manifest"))
            .map(o -> (Map<String, Object>) o.get("spec"))
            .map(o -> (Map<String, Object>) o.get("template"))
            .map(o -> (Map<String, Object>) o.get("spec"))
            .map(o -> (List<Map<String, Object>>) o.get("containers"))
            .orElse(emptyList());

    List<Artifact> requiredArtifacts = new ArrayList<>();
    List<String> artifactIds =
        Optional.ofNullable(runJobManifestContext.getRequiredArtifactIds()).orElse(emptyList());
    for (String id : artifactIds) {
      Artifact requiredArtifact = artifactResolver.getBoundArtifactForId(stage, id);
      if (requiredArtifact == null) {
        throw new IllegalStateException(
            "No artifact with id '" + id + "' could be found in the pipeline context.");
      }

      requiredArtifacts.add(requiredArtifact);
    }

    for (Map<String, Object> container : containers) {
      List<Map<String, Object>> envs =
          (List<Map<String, Object>>) Optional.ofNullable(container.get("env")).orElse(emptyList());
      for (Map<String, Object> env : envs) {
        String key = (String) env.get("name");
        if (key != null && key.startsWith(PREFIX_CUSTOM_ENV)) {
          injectContent(env, requiredArtifacts);
        }
      }
    }
  }

  private void injectContent(Map<String, Object> envItem, List<Artifact> requiredArtifacts) {
    Artifact manifestArtifact = objectMapper.convertValue(envItem.get("value"), Artifact.class);
    String rawManifests =
        retrySupport.retry(
            () -> {
              try {
                Response manifestText = oort.fetchArtifact(manifestArtifact);
                if (manifestText.getStatus() == 200) {
                  return IOUtils.toString(manifestText.getBody().in());
                }
                throw new IllegalStateException(
                    String.format("fetchArtifact error: %s", manifestText.getStatus()));
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            },
            10,
            Duration.ofMillis(200),
            true); // retry 10x, starting at .2s intervals);
    log.debug("YAML_CONTENT raw\n{}", rawManifests);

    if (rawManifests != null) {
      for (Artifact artifact : requiredArtifacts) {
        rawManifests = rawManifests.replace(artifact.getName(), artifact.getReference());
      }
      log.debug("YAML_CONTENT raw replace artifact\n{}", rawManifests);
      envItem.put("value", rawManifests);
    } else {
      log.error("YAML_CONTENT raw is empty");
    }
  }

  public Map<String, Object> getAdditionalOutputs(Stage stage, List<Map> operations) {
    Map<String, Object> outputs = new HashMap<>();
    Map<String, Object> execution = new HashMap<>();

    // if the manifest contains the template annotation put it into the context
    if (stage.getContext().containsKey("manifest")) {
      Manifest manifest =
          objectMapper.convertValue(stage.getContext().get("manifest"), Manifest.class);
      String logTemplate = ManifestAnnotationExtractor.logs(manifest);
      if (logTemplate != null) {
        execution.put("logs", logTemplate);
        outputs.put("execution", execution);
      }
    }

    return outputs;
  }
}
