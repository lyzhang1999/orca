package com.netflix.spinnaker.orca.grpc;

import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(value = "cd.coding.grpc")
public class CdGrpcSettings {
  @NotNull private String host;
  @NotNull private int port;
}
