package com.nook.biz.node.framework.xray.grpc;

import jakarta.annotation.Resource;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.PortForward;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.common.web.exception.BusinessException;
import com.xray.app.stats.command.GetStatsRequest;
import com.xray.app.stats.command.GetStatsResponse;
import com.xray.app.stats.command.StatsServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Xray StatsService gRPC 客户端: 用户 / inbound 流量读取, 每次现建 ManagedChannel (复用 SshSession 缓存的 PortForward).
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayStatsClient {

    @Resource
    private SshSessionManager sshSessionManager;
    @Resource
    private ResourceServerApi resourceServerApi;

    /**
     * 读字节计数 stat, reset=true 原子返回旧值并清零; NOT_FOUND/UNKNOWN 当 0 处理 (用户从未产生流量时 Xray 不建 stat 槽).
     *
     * @param serverId resource_server.id
     * @param statName Xray stat key (如 user>>>email>>>traffic>>>uplink)
     * @param reset    读后是否清零
     * @return 字节数
     */
    public long readStat(String serverId, String statName, boolean reset) {
        ServerCredentialDTO cred = loadXrayCred(serverId);
        ManagedChannel ch = openChannel(serverId, cred);
        try {
            GetStatsResponse resp = StatsServiceGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(cred.backendTimeoutSeconds(), TimeUnit.SECONDS)
                    .getStats(GetStatsRequest.newBuilder()
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
                log.warn("[grpc] readStat UNAVAILABLE server={} stat={}", serverId, statName, sre);
                throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, sre, serverId);
            }
            log.warn("[grpc] readStat 失败 server={} stat={}", serverId, statName, sre);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, sre,
                    serverId, "readStat: " + sre.getStatus());
        } finally {
            ch.shutdownNow();
        }
    }

    /** 取 cred 并校验 xrayGrpcHost; gRPC 通道专属字段, SshSession 不要求. */
    private ServerCredentialDTO loadXrayCred(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        if (StrUtil.isBlank(cred.xrayGrpcHost())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, serverId);
        }
        return cred;
    }

    /** 拿 SshSession 上 cache 好的 PortForward, 现建 ManagedChannel; 调用方负责 finally shutdownNow. */
    private ManagedChannel openChannel(String serverId, ServerCredentialDTO cred) {
        SshSession ssh = sshSessionManager.acquire(serverId);
        PortForward fw = ssh.openLocalForward(cred.xrayGrpcHost(), cred.xrayGrpcPort());
        return ManagedChannelBuilder.forAddress("127.0.0.1", fw.localPort())
                .usePlaintext()
                .build();
    }

    private static boolean isTransient(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }
}
