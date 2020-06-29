package com.netflix.spinnaker.orca.grpc.client;

import com.netflix.spinnaker.orca.grpc.CodingGrpcSettings;
import lombok.extern.slf4j.Slf4j;
import net.coding.e.proto.TeamProto;
import net.coding.e.proto.TeamServiceGrpc;
import org.springframework.stereotype.Component;

/**
 * this module only use for CD
 *
 * @author wangwei CD-Group
 * @date 2020/06/28 6:19 下午
 */
@Component
@Slf4j
public class ECodingGrpcClient extends CodingBaseClient {
  public ECodingGrpcClient(CodingGrpcSettings grpcSettings) {
    super(grpcSettings);
  }

  private TeamServiceGrpc.TeamServiceBlockingStub getStub() {
    return TeamServiceGrpc.newBlockingStub(openNewChannel());
  }

  public void getTeamByTeamId(Integer teamId) {
    try {
      TeamProto.TeamRequest request = TeamProto.TeamRequest.newBuilder().setTeamId(teamId).build();
      getStub().getTeamByTeamId(request);
    } catch (Exception e) {
      log.error("getTeamByTeamId error ", e);
    }
  }
}
