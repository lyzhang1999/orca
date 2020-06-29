package com.netflix.spinnaker.orca.grpc.client;

import com.netflix.spinnaker.orca.grpc.CodingGrpcSettings;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * this module only use for CD
 *
 * @author wangwei CD-Group
 * @date 2020/06/28 6:14 下午
 */
@Component
@Slf4j
public class CodingBaseClient {

  public CodingBaseClient(CodingGrpcSettings grpcSettings) {
    this.grpcSettings = grpcSettings;
  }

  private final CodingGrpcSettings grpcSettings;

  private static final int GRPC_MAX_MESSAGE_SIZE = 100 * 1024 * 1024;
  private static final Map<String, ManagedChannel> CHANNELS = new ConcurrentHashMap<>();

  public Channel openChannel() {
    return CHANNELS.computeIfAbsent(
        this.getClass().getSimpleName(),
        k ->
            NettyChannelBuilder.forAddress(grpcSettings.getHost(), grpcSettings.getPort())
                .maxInboundMetadataSize(GRPC_MAX_MESSAGE_SIZE)
                .usePlaintext()
                .build());
  }

  public ManagedChannel openNewChannel() {
    return NettyChannelBuilder.forAddress(grpcSettings.getHost(), grpcSettings.getPort())
        .maxInboundMetadataSize(GRPC_MAX_MESSAGE_SIZE)
        .usePlaintext()
        .build();
  }
}
