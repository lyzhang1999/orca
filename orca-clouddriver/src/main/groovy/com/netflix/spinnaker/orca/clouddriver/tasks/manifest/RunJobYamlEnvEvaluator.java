/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import io.micrometer.core.instrument.util.IOUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import retrofit.client.Response;

@Component
@Slf4j
public class RunJobYamlEnvEvaluator {

  private static final String PREFIX_CUSTOM_ENV = "YAML_CONTENT";
  private final RetrySupport retrySupport = new RetrySupport();
  @Autowired ArtifactResolver artifactResolver;
  @Autowired OortService oort;
  @Autowired private ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  public void replaceEnvArtifacts(Stage stage) {
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
        rawManifests = yamlVersionReplace(rawManifests, artifact.getReference());
      }
      log.debug("YAML_CONTENT raw replace artifact\n{}", rawManifests);
      envItem.put("value", rawManifests);
    } else {
      log.error("YAML_CONTENT raw is empty");
    }
  }

  /**
   * 为 yaml 文件中未写版本号的镜像填充版本号，已写版本号的同名镜像不做替换
   *
   * @param yamlStr 可包含多个资源的 yaml 格式字符串
   * @param dockerImage 镜像版本，格式为（镜像全名:镜像版本）
   * @return 替换版本后的 yaml 字符串
   */
  private String yamlVersionReplace(String yamlStr, String dockerImage) {
    try {
      Yaml yamlOjb = new Yaml();
      if (dockerImage == null) {
        return yamlStr;
      }
      if (!dockerImage.contains(":")) {
        dockerImage += ":latest";
      }
      String[] split = dockerImage.split(":");
      if (split.length != 2) {
        throw new IllegalArgumentException();
      }

      String image = split[0];
      StringBuilder yamlBuilder = new StringBuilder();
      Iterable<Object> objects = yamlOjb.loadAll(yamlStr);
      for (Object obj : objects) {
        String json =
            objectMapper
                .writeValueAsString(obj)
                .replaceAll("\"image\":\"" + image + "\"", "\"image\":\"" + dockerImage + "\"");
        yamlBuilder.append(convertJsonToYaml(json));
      }

      String result = yamlBuilder.toString();
      if (result.startsWith("---\n")) {
        result = result.substring(4);
      }
      return result;
    } catch (Exception e) {
      log.error("版本替换异常，Exception {}\nyaml content:{}\ndockerImage: {}", e, yamlStr, dockerImage);
      throw new IllegalArgumentException(e);
    }
  }

  private String convertJsonToYaml(String json) throws Exception {
    JsonNode jsonNodeTree = objectMapper.readTree(json);
    return new YAMLMapper().writeValueAsString(jsonNodeTree);
  }
}
