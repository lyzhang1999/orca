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

package com.netfilx.spinnaker.orca.coding.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class TeamHelper {

  public static final String TEAM = "team";

  public static String getTeamSuffix(String teamId) {
    return StringUtils.join(TEAM + teamId);
  }

  public static String getTargetSuffix(String target) {
    Pattern pattern = Pattern.compile("(?i)team\\d+$");
    Matcher matcher = pattern.matcher(target);
    if (matcher.find()) {
      return matcher.group();
    }
    return "";
  }

  public static Integer getTeamId(String target) {
    String[] split = target.split(TEAM);
    if (split.length > 1) {
      return Integer.parseInt(split[split.length - 1]);
    }
    return null;
  }

  public static String withSuffix(String teamId, String target) {
    if (StringUtils.isEmpty(target)) {
      return target;
    }
    return StringUtils.join(target, TEAM + teamId);
  }

  public static String replaceSuffix(String target) {
    if (StringUtils.isEmpty(target)) {
      return target;
    }
    return target.replaceFirst("(?i)team\\d+$", "");
  }
}
