package com.netflix.spinnaker.orca.grpc.client;

import com.netflix.spinnaker.orca.grpc.CdGrpcSettings;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BaseClient {

  public BaseClient(CdGrpcSettings grpcSettings) {
    this.grpcSettings = grpcSettings;
  }

  private final CdGrpcSettings grpcSettings;

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
