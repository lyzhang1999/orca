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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class BakeContext {
  @Nullable private Map<Object, Object> manifest;

  private Source source;

  private String manifestArtifactId;
  private String manifestArtifactAccount;
  private Artifact manifestArtifact;

  private boolean skipExpressionEvaluation = false;

  enum Source {
    @JsonProperty("text")
    Text,

    @JsonProperty("artifact")
    Artifact
  }
}
