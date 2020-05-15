package com.netflix.spinnaker.orca.grpc.client;

import com.netflix.spinnaker.orca.grpc.CdGrpcSettings;
import lombok.extern.slf4j.Slf4j;
import net.coding.cd.proto.GrpcPipelineServiceGrpc;
import net.coding.cd.proto.PipelineProto;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CodingGrpcClient extends BaseClient {

  public CodingGrpcClient(CdGrpcSettings grpcSettings) {
    super(grpcSettings);
  }

  private GrpcPipelineServiceGrpc.GrpcPipelineServiceBlockingStub getStub() {
    return GrpcPipelineServiceGrpc.newBlockingStub(openNewChannel());
  }

  public void deletePipeline(String pipelineId) {
    try {
      PipelineProto.DeletePipelineRequest request =
          PipelineProto.DeletePipelineRequest.newBuilder().setPipelineId(pipelineId).build();
      getStub().deletePipeline(request);
    } catch (Exception e) {
      log.error("deletePipeline error ", e);
    }
  }
}
