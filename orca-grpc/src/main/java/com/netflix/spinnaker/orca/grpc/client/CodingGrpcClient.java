package com.netflix.spinnaker.orca.grpc.client;

import static net.coding.cd.proto.PipelineProto.*;

import com.netflix.spinnaker.orca.grpc.CdGrpcSettings;
import lombok.extern.slf4j.Slf4j;
import net.coding.cd.proto.GrpcPipelineServiceGrpc;
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
      DeletePipelineRequest request =
          DeletePipelineRequest.newBuilder().setPipelineId(pipelineId).build();
      getStub().deletePipeline(request);
    } catch (Exception e) {
      log.error("deletePipeline error ", e);
    }
  }

  public void deletePipelineByTeamIdAndApplication(int teamId, String application) {
    try {
      DeleteApplicationRequest request =
          DeleteApplicationRequest.newBuilder()
              .setTeamId(teamId)
              .setApplication(application)
              .build();
      getStub().deleteApplication(request);
    } catch (Exception e) {
      log.error("deleteApplication error ", e);
    }
  }
}
