/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo

import com.fasterxml.jackson.annotation.JsonInclude
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.GET
import retrofit.http.Header
import retrofit.http.Headers
import retrofit.http.POST
import retrofit.http.Path

interface EchoService {

  @POST("/")
  Response recordEvent(@Body Map<String, ?> notification)

  @GET("/events/recent/{type}/{since}/")
  Response getEvents(@Path("type") String type, @Path("since") Long since)

  @Headers("Content-type: application/json")
  @POST("/notifications")
  Response create(@Body Notification notification)

  // TCR TRIGGER
  @DELETE("/tcr/trigger/{region}/{instanceId}/{namespaceId}/{triggerId}")
  Response deleteTcrTrigger(@Header("X-SPINNAKER-USER") String userGK, @Path("region") String region, @Path("instanceId") String instanceId, @Path("namespaceId") Integer namespaceId, @Path("triggerId") Integer triggerId)

  @GET("/tcr/trigger/list/{region}/{instanceId}")
  List<Map<String, Object>> getTcrTriggerList(@Header("X-SPINNAKER-USER") String userGK, @Path("region") String region, @Path("instanceId") String instanceId)

  @Headers("Content-type: application/json")
  @POST("/tcr/trigger")
  Response createTcrTrigger(@Header("X-SPINNAKER-USER") String userGK, @Body TcrTrigger triggerData)


  static class TcrTrigger {
    String userGK
    String region
    String instance
    String instanceId
    Integer namespaceId
    Integer triggerId
    String name
    String description
    String condition
    String address
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class Notification {
    Type notificationType
    Collection<String> to
    Collection<String> cc
    String templateGroup
    Severity severity

    Source source
    Map<String, Object> additionalContext = [:]

    static class Source {
      String executionType
      String executionId
      String application
      String user
    }

    static enum Type {
      BEARYCHAT,
      EMAIL,
      GOOGLECHAT,
      HIPCHAT,
      JIRA,
      PAGER_DUTY,
      PUBSUB,
      SLACK,
      SMS,
      EWECHAT,
      ECODING,
      EDINGTALK,
      EBEARYCHAT
    }

    static enum Severity {
      NORMAL,
      HIGH
    }
  }

}
