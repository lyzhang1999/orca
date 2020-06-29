package com.netflix.spinnaker.orca.grpc;

import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * this module only use for CD
 *
 * @author wangwei CD-Group
 * @date 2020/06/28 6:13 下午
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(value = "cd.coding.ecoding")
public class CodingGrpcSettings {
  @NotNull private String host;
  @NotNull private int port;
}
