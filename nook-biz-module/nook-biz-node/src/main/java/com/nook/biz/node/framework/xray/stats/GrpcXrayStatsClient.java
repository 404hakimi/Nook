package com.nook.biz.node.framework.xray.stats;

import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.xray.app.stats.command.GetStatsRequest;
import com.xray.app.stats.command.GetStatsResponse;
import com.xray.app.stats.command.StatsServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/** XrayStatsClient 的 gRPC 实现; 复用宿主 session 的 ManagedChannel. */
@Slf4j
@RequiredArgsConstructor
public class GrpcXrayStatsClient implements XrayStatsClient {

    private final ServerCredentialDTO cred;
    private final ManagedChannel channel;

    @Override
    public void verifyConnectivity() {
        try {
            // 探活走 api inbound 自身的上行 stat — 任何 RPC 必经它, 必定存在
            statsStub().getStats(GetStatsRequest.newBuilder()
                    .setName(GrpcConstants.STAT_API_INBOUND_UPLINK)
                    .build());
        } catch (StatusRuntimeException sre) {
            log.warn("[grpc] verifyConnectivity 失败 server={}", cred.serverId(), sre);
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, sre, cred.serverId());
        }
    }

    @Override
    public long readStat(String statName, boolean reset) {
        try {
            GetStatsResponse resp = statsStub().getStats(GetStatsRequest.newBuilder()
                    .setName(statName)
                    .setReset(reset)
                    .build());
            return resp.hasStat() ? resp.getStat().getValue() : 0L;
        } catch (StatusRuntimeException sre) {
            Status.Code code = sre.getStatus().getCode();
            // 用户从未产生流量时 Xray 不建 stat 槽, NOT_FOUND/UNKNOWN 当 0 处理
            if (code == Status.Code.NOT_FOUND || code == Status.Code.UNKNOWN) {
                return 0L;
            }
            if (isTransient(code)) {
                log.warn("[grpc] readStat UNAVAILABLE server={} stat={}", cred.serverId(), statName, sre);
                throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, sre, cred.serverId());
            }
            log.warn("[grpc] readStat 失败 server={} stat={}", cred.serverId(), statName, sre);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, sre,
                    cred.serverId(), "readStat: " + sre.getStatus());
        }
    }

    private StatsServiceGrpc.StatsServiceBlockingStub statsStub() {
        return StatsServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(cred.backendTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private static boolean isTransient(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }
}
