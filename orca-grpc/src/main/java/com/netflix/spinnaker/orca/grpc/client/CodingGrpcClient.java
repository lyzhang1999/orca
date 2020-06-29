package com.netflix.spinnaker.orca.grpc.client;

import static net.coding.cd.proto.PipelineProto.*;

import com.netflix.spinnaker.orca.grpc.CdGrpcSettings;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.coding.cd.proto.*;
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

  private GrpcManualJudgeServiceGrpc.GrpcManualJudgeServiceBlockingStub getManualStub() {
    return GrpcManualJudgeServiceGrpc.newBlockingStub(openNewChannel());
  }

  private GrpcApplicationServiceGrpc.GrpcApplicationServiceBlockingStub getApplicationStub() {
    return GrpcApplicationServiceGrpc.newBlockingStub(openNewChannel());
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

  public List<ApplicationProto.Application> getCodingApplication(
      String teamGk, String applicationName) {
    try {
      ApplicationProto.GetApplicationRequest request =
          ApplicationProto.GetApplicationRequest.newBuilder()
              .setTeamGk(teamGk)
              .setApplicationName(applicationName)
              .build();
      ApplicationProto.GetApplicationResponse response =
          getApplicationStub().getApplication(request);
      return response.getApplicationList();
    } catch (StatusRuntimeException e) {
      log.warn("Grpc coding-cd getCodingApplication error {}", e);
    }
    List<ApplicationProto.Application> list = new ArrayList<>();
    list.add(ApplicationProto.Application.getDefaultInstance());
    return list;
  }

  public void insertOrUpdateManualJudge(Map manualJudge) {
    try {
      ManualJudge.manualJudgeRequest request =
          ManualJudge.manualJudgeRequest
              .newBuilder()
              .setStageId(manualJudge.get("stageId").toString())
              .setPipelineId(manualJudge.get("pipelineId").toString())
              .setPipelineConfigId(manualJudge.get("pipelineId").toString())
              .setPipelineConfigName(manualJudge.get("pipelineConfigName").toString())
              .setApplication(manualJudge.get("application").toString())
              .setTeamId(Integer.parseInt(manualJudge.get("teamId").toString()))
              .setCreateTime(Integer.parseInt(manualJudge.get("createTime").toString()))
              .setLinkUrl(manualJudge.get("createTime").toString())
              .setUserGKs(manualJudge.get("userGKs").toString())
              .setUserAvatars(manualJudge.get("userAvatars").toString())
              .setStatus(Integer.parseInt(manualJudge.get("userAvatars").toString()))
              .build();
      getManualStub().insertOrUpdateManualJudge(request);
    } catch (Exception e) {
      log.error("insertOrUpdateManualJudge error ", e);
    }
  }
}
