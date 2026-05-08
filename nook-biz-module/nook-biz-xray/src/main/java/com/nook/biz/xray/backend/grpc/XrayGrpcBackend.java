package com.nook.biz.xray.backend.grpc;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.backend.XrayBackend;
import com.nook.biz.xray.backend.XrayProtocol;
import com.nook.biz.xray.backend.dto.XrayClientRef;
import com.nook.biz.xray.backend.dto.XrayClientSpec;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.constant.XrayConstants;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.biz.xray.util.SshExecutor;
import com.nook.biz.xray.util.SshTunnel;
import com.nook.common.web.exception.BusinessException;
import com.xray.app.proxyman.command.AddUserOperation;
import com.xray.app.proxyman.command.AlterInboundRequest;
import com.xray.app.proxyman.command.HandlerServiceGrpc;
import com.xray.app.proxyman.command.RemoveUserOperation;
import com.xray.app.stats.command.GetStatsRequest;
import com.xray.app.stats.command.GetStatsResponse;
import com.xray.app.stats.command.StatsServiceGrpc;
import com.xray.common.protocol.User;
import com.xray.common.serial.TypedMessage;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Xray 原生 gRPC API 的 backend 实现; 一个实例对应一台 resource_server, 复用 SSH 隧道转发的 channel.
 *
 * <p>能力分层: HandlerService 负责 inbound user 的运行时增删 (无重启);
 * StatsService 提供按 user/inbound/outbound 维度的字节计数; 出站与路由规则的持久化变更由
 * {@link com.nook.biz.xray.service.XrayConfigReconciler} 走 SSH 重写 xray.json 完成。
 */
@Slf4j
public class XrayGrpcBackend implements XrayBackend, AutoCloseable {

    private final ServerCredentialDTO cred;
    private final SshExecutor sshExecutor;
    /** 与 channel 1:1 绑定的 SSH 隧道; 拥有权由 backend 持有, close 时一起释放. */
    private final SshTunnel tunnel;
    private final ManagedChannel channel;

    public XrayGrpcBackend(ServerCredentialDTO cred, SshExecutor sshExecutor) {
        if (StrUtil.isBlank(cred.xrayGrpcHost())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        this.cred = cred;
        this.sshExecutor = sshExecutor;
        // gRPC 直连的是 nook 本地端口, 由 SSH 隧道把它转发到远端 cred.xrayGrpcHost:Port;
        // 远端通常 listen 127.0.0.1, 公网不暴露。
        // tunnel 与 channel 强绑定: 隧道 local port 一旦失效 (ssh 断开/重建), 整个 backend 必须丢弃,
        // 不能通过 manager 复用 — 否则 channel 仍指向旧端口导致 UNAVAILABLE。
        this.tunnel = SshTunnel.open(cred, cred.xrayGrpcHost(), cred.xrayGrpcPort());
        this.channel = ManagedChannelBuilder.forAddress("127.0.0.1", tunnel.localPort())
                .usePlaintext()
                .build();
    }

    /**
     * Backend 是否仍可用; factory 在分发前检查, 不可用则丢弃重建。
     * channel 的 connectivity state 仅做廉价探测(不发包), 真正的死活以 tunnel 为准 —
     * SSH 关了 socket 就关了, channel 这边可能还显示 IDLE/READY, 但下次 RPC 会拿 UNAVAILABLE。
     */
    public boolean isAlive() {
        if (channel.isShutdown() || channel.isTerminated()) return false;
        if (channel.getState(false) == ConnectivityState.SHUTDOWN) return false;
        return tunnel.isAlive();
    }

    @Override
    public String serverId() {
        return cred.serverId();
    }

    /**
     * 探活: 调一次 StatsService.GetStats(api inbound 上行) — 该 stat 在 statsInboundUplink=true
     * 启用后必定存在 (api inbound 自身的流量, nook 每次 RPC 都走它). 任何 gRPC 异常都视为不可达, 包装抛出。
     */
    @Override
    public void verifyConnectivity() {
        try {
            statsStub().getStats(GetStatsRequest.newBuilder()
                    .setName(XrayConstants.STAT_API_INBOUND_UPLINK)
                    .build());
        } catch (StatusRuntimeException sre) {
            log.warn("[grpc] verifyConnectivity 失败 server={}", cred.serverId(), sre);
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, sre, cred.serverId());
        }
    }

    /** Xray gRPC 没原生 list 接口; 走 SSH 读 xray.json 解析 inbound 列表 (跳过 api 通道)。 */
    @Override
    public List<XrayInboundInfo> listInbounds() {
        String json = sshExecutor.exec(cred,
                "cat " + XrayConstants.REMOTE_CONFIG_PATH + " 2>/dev/null || echo '{}'");
        return XrayConfigParser.parseInbounds(json);
    }

    @Override
    public void addClient(XrayClientSpec spec) {
        XrayProtocol protocol = XrayProtocol.of(spec.protocol());
        TypedMessage account = protocol.buildAccountTypedMessage(spec);
        User user = User.newBuilder()
                .setLevel(0)
                .setEmail(StrUtil.blankToDefault(spec.email(), ""))
                .setAccount(account)
                .build();
        AddUserOperation op = AddUserOperation.newBuilder().setUser(user).build();
        try {
            handlerStub().alterInbound(AlterInboundRequest.newBuilder()
                    .setTag(spec.externalInboundRef())
                    .setOperation(wrapOp(AddUserOperation.getDescriptor().getFullName(), op.toByteString()))
                    .build());
            log.info("[grpc] addClient server={} inbound={} email={} protocol={}",
                    cred.serverId(), spec.externalInboundRef(), spec.email(), protocol.getCode());
        } catch (StatusRuntimeException sre) {
            throw mapAlterInboundError(sre, "addClient", spec.email(),
                    XrayConstants.GRPC_DESC_USER_DUPLICATE, XrayErrorCode.CLIENT_DUPLICATE);
        }
    }

    @Override
    public void delClient(XrayClientRef ref) {
        if (StrUtil.isBlank(ref.email())) {
            // RemoveUserOperation 仅按 email 索引, 没有 email 等价于无法定位
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    cred.serverId(), "delClient 缺 email");
        }
        RemoveUserOperation op = RemoveUserOperation.newBuilder().setEmail(ref.email()).build();
        try {
            handlerStub().alterInbound(AlterInboundRequest.newBuilder()
                    .setTag(ref.externalInboundRef())
                    .setOperation(wrapOp(RemoveUserOperation.getDescriptor().getFullName(), op.toByteString()))
                    .build());
            log.info("[grpc] delClient server={} inbound={} email={}",
                    cred.serverId(), ref.externalInboundRef(), ref.email());
        } catch (StatusRuntimeException sre) {
            throw mapAlterInboundError(sre, "delClient", ref.email(),
                    XrayConstants.GRPC_DESC_USER_NOT_FOUND, XrayErrorCode.CLIENT_NOT_FOUND);
        }
    }

    @Override
    public XrayClientTraffic getClientTraffic(XrayClientRef ref) {
        return readUserTraffic(ref.email(), false);
    }

    @Override
    public void resetClientTraffic(XrayClientRef ref) {
        readUserTraffic(ref.email(), true);
    }

    /** 按 email 拉 uplink/downlink; reset=true 会原子返回旧值并清零。 */
    private XrayClientTraffic readUserTraffic(String email, boolean reset) {
        if (StrUtil.isBlank(email)) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    cred.serverId(), "getTraffic 缺 email");
        }
        long up = readSingleStat(String.format(XrayConstants.STAT_USER_UPLINK_FORMAT, email), reset);
        long down = readSingleStat(String.format(XrayConstants.STAT_USER_DOWNLINK_FORMAT, email), reset);
        // 流量上限 / 到期 / enabled 在 gRPC 模式下不由 Xray 维护, 由 nook subscription 控制
        return new XrayClientTraffic(email, up, down, 0L, 0L, true);
    }

    /** 该用户从未产生流量时 Xray 不会建 stat 槽, 返回 NOT_FOUND/UNKNOWN, 我们当 0 处理。 */
    private long readSingleStat(String name, boolean reset) {
        try {
            GetStatsResponse resp = statsStub().getStats(GetStatsRequest.newBuilder()
                    .setName(name)
                    .setReset(reset)
                    .build());
            return resp.hasStat() ? resp.getStat().getValue() : 0L;
        } catch (StatusRuntimeException sre) {
            Status.Code code = sre.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND || code == Status.Code.UNKNOWN) {
                return 0L;
            }
            log.warn("[grpc] getStats 失败 server={} stat={}", cred.serverId(), name, sre);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, sre,
                    cred.serverId(), "getStats: " + sre.getStatus());
        }
    }

    /**
     * AlterInbound 的 Add/Remove 共享错误形态: gRPC code 多为 UNKNOWN, description 是字符串描述。
     * 命中给定关键词则映射为业务错误码, 否则统一 BACKEND_OPERATION_FAILED 包装抛出 (保留 cause)。
     */
    private BusinessException mapAlterInboundError(StatusRuntimeException sre,
                                                   String op,
                                                   String email,
                                                   String matchDescriptionKeyword,
                                                   XrayErrorCode mappedCode) {
        String desc = StrUtil.blankToDefault(sre.getStatus().getDescription(), "");
        if (StrUtil.containsIgnoreCase(desc, matchDescriptionKeyword)) {
            return new BusinessException(mappedCode, email);
        }
        log.warn("[grpc] {} 失败 server={} email={} status={}",
                op, cred.serverId(), email, sre.getStatus());
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, sre,
                cred.serverId(), op + ": " + sre.getStatus());
    }

    private TypedMessage wrapOp(String typeFullName, com.google.protobuf.ByteString value) {
        return TypedMessage.newBuilder().setType(typeFullName).setValue(value).build();
    }

    private HandlerServiceGrpc.HandlerServiceBlockingStub handlerStub() {
        return HandlerServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(cred.backendTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private StatsServiceGrpc.StatsServiceBlockingStub statsStub() {
        return StatsServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(cred.backendTimeoutSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        if (!channel.isShutdown()) {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        tunnel.close();
    }
}
