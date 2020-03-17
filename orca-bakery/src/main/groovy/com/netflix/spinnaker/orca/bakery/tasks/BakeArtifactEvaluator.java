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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import retrofit.client.Response;

@Component
@Slf4j
@RequiredArgsConstructor
public class BakeArtifactEvaluator {

  private final ArtifactResolver artifactResolver;
  private final OortService oort;
  private final ObjectMapper objectMapper;
  private final ContextParameterProcessor contextParameterProcessor;

  private final RetrySupport retrySupport = new RetrySupport();

  public String evaluateProvisioners(Stage stage, BakeContext context) throws IOException {
    // handle user shell provisioners $.manifest.provisioners[*]
    Optional.ofNullable(context.getProvisioners())
        .ifPresent(
            provisioners -> {
              for (Map provisioner : provisioners) {
                String type = (String) provisioner.get("type");
                //region 处理 script provisioner
                String script = (String) provisioner.getOrDefault("script","");
                // 用户自己设置的 shell provisioners
                if (type.equals("shell") && !script.equals("{{user `configDir`}}/install_packages.sh")){
                  final Pattern pattern = Pattern.compile("\\{\\{artifact\\s+`(.*)`\\}\\}");
                  Matcher matcher = pattern.matcher(script);
                  if(matcher.matches()){
                    // 用户在shell provisioners 引用了一个制品
                    String scriptArtifactId = matcher.group(1);
                    Artifact scriptArtifact = artifactResolver.getBoundArtifactForStage(stage, scriptArtifactId, null);
                    String scriptText =
                        retrySupport.retry(
                            () -> {
                              Response response = oort.fetchArtifact(scriptArtifact);
                              try (InputStream body = response.getBody().in()) {
                                return IOUtils.toString(body, StandardCharsets.UTF_8);
                              } catch (Exception e) {
                                log.warn("Failure fetching/parsing scriptArtifact from {}", scriptArtifact, e);
                                // forces a retry
                                throw new IllegalStateException(e);
                              }
                            },
                            10,
                            200,
                            true); // retry 10x, starting at .2s intervals);

                    // 把下载完的 script text 替换回去
                    provisioner.put("content", scriptText);
                    provisioner.remove("script");
                  }
                }
                //endregion

                //region 处理 file provisioner
                if (type.equals("file")){
                  String source = (String) provisioner.getOrDefault("source","");
                  // 防止用户注入 artifact json 文件，这个文件里面可引用别人的文件而不会被检查到
                  String destination = (String) provisioner.getOrDefault("destination","");
                  if(destination.equalsIgnoreCase("/tmp/artifacts.json")){
                    throw new IllegalArgumentException("destination /tmp/artifacts.json is protected!");
                  }
                  final Pattern pattern = Pattern.compile("\\{\\{artifact\\s+`(.*)`\\}\\}");
                  Matcher matcher = pattern.matcher(source);
                  if(matcher.matches()){
                    // 用户在file provisioners 引用了一个制品
                    String scriptArtifactId = matcher.group(1);
                    Artifact fileArtifact = artifactResolver.getBoundArtifactForStage(stage, scriptArtifactId, null);
                    try {
                      provisioner.put("source", objectMapper.writeValueAsString(fileArtifact));
                    } catch (JsonProcessingException e) {
                      e.printStackTrace();
                    }
                  }
                  else {
                    //用户输入的文件或别的东西，不支持，防止窃取别的用户的文件
                    throw new IllegalArgumentException("can not use file path source in file provisioner, please use artifactId ");
                  }
                }
                // endregion

                // check local shell Provisioner 防止恶意脚本
                if (type.equals("shell-local")) {
                  log.warn("packer 配置使用了 shell-local provisioner ");
                  throw new IllegalArgumentException("can not use shell-local provisioner");
                }
              }
            });
    String manifestStr = objectMapper.writeValueAsString(context.getManifest());
    log.info("json manifest evaluuator got result : {}", manifestStr);
    return manifestStr;
  }

  public String evaluateArtifactReference(Artifact artifact) {
    return retrySupport.retry(
        () -> {
          try {
            return (String)oort.getArtifactUrl(artifact).getOrDefault("result",null);
          } catch (Exception e) {
            log.warn("Failure get artifact url  from {}", artifact, e);
            // forces a retry
            throw new IllegalStateException(e);
          }
        },
        10,
        200,
        true);
  }
}
