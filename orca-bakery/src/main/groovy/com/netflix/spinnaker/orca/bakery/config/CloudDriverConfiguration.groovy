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

package com.netflix.spinnaker.orca.bakery.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy
import com.netflix.spinnaker.orca.bakery.BakerySelector
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.tasks.OortService
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowire
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.converter.JacksonConverter

import java.text.SimpleDateFormat

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import([OrcaConfiguration, RetrofitConfiguration])
@CompileStatic
@ComponentScan([
        "com.netflix.spinnaker.orca.bakery.pipeline",
        "com.netflix.spinnaker.orca.bakery.tasks"
])
@ConditionalOnExpression('${bakery.enabled:true}')
@EnableConfigurationProperties(CloudDriverConfigurationProperties)
@Slf4j
class CloudDriverConfiguration {

  @Autowired Client retrofitClient
  @Autowired LogLevel retrofitLogLevel
  @Autowired RequestInterceptor spinnakerRequestInterceptor
  @Autowired CloudDriverConfigurationProperties config;

  @Bean
  OortService oortService() {
    return buildService(config.cloudDriverBaseUrl)
  }

  OortService buildService(String url) {
    return new RestAdapter.Builder()
            .setEndpoint(newFixedEndpoint(url))
            .setRequestInterceptor(spinnakerRequestInterceptor)
            .setConverter(new JacksonConverter(bakeryConfiguredObjectMapper()))
            .setClient(retrofitClient)
            .setLogLevel(retrofitLogLevel)
            .setLog(new RetrofitSlf4jLog(OortService))
            .build()
            .create(OortService)
  }

  static ObjectMapper bakeryConfiguredObjectMapper() {
    def objectMapper = new ObjectMapper()
      .setPropertyNamingStrategy(new SnakeCaseStrategy())
      .setDateFormat(new SimpleDateFormat("YYYYMMDDHHmm"))
      .setSerializationInclusion(NON_NULL)
      .disable(FAIL_ON_UNKNOWN_PROPERTIES)
  }
}
