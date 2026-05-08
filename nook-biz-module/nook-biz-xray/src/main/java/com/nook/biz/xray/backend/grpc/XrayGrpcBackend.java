package com.nook.biz.xray.backend.grpc;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.backend.XrayBackend;
import com.nook.biz.xray.backend.dto.XrayClientRef;
import com.nook.biz.xray.backend.dto.XrayClientSpec;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.biz.xray.util.SshTunnel;
import com.nook.biz.xray.util.SshTunnelManager;
import com.nook.common.web.exception.BusinessException;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Xray 内核原生 gRPC API 对接实现。
 *
 * <p><b>当前状态：骨架</b>。Channel 生命周期 + verifyConnectivity 已就绪，
 * 业务方法(listInbounds/addClient/delClient/getClientTraffic/resetClientTraffic)
 * 全部抛 {@link XrayErrorCode#GRPC_NOT_IMPLEMENTED}，等接入 Xray proto 后再补。
 *
 * <p>接入步骤(后续工作):
 * <ol>
 *   <li>把 xray-core 的 proto 文件 vendor 进 {@code src/main/proto/}：
 *     <ul>
 *       <li>{@code common/serial/typed_message.proto}</li>
 *       <li>{@code app/proxyman/command/command.proto}</li>
 *       <li>{@code app/stats/command/command.proto}</li>
 *       <li>{@code proxy/vless/account.proto}, {@code proxy/vmess/account.proto},
 *           {@code proxy/trojan/account.proto}(取需要的协议)</li>
 *     </ul>
 *   </li>
 *   <li>{@code mvn compile} 触发 protobuf-maven-plugin 生成 Java stub(已在 pom 配置)。</li>
 *   <li>实现 addClient: 用 {@code HandlerService.AlterInbound} 的 {@code AddUser} 操作，
 *       Account 字段按 protocol 塞 vless.Account / vmess.Account / trojan.Account。</li>
 *   <li>实现 delClient: 同上的 {@code RemoveUser}。</li>
 *   <li>实现 getClientTraffic: {@code StatsService.GetStats} 查
 *       {@code user>>>{email}>>>traffic>>>uplink/downlink}。</li>
 *   <li>实现 resetClientTraffic: {@code StatsService.GetStats} reset=true 同时拿值清零；
 *       或 {@code QueryStats} 配 reset。</li>
 *   <li>listInbounds: gRPC 没直接的 list，可读 Xray 启动配置(读 SSH 上 /usr/local/etc/xray/config.json)
 *       或维护 nook 侧的 inbound 元数据表。</li>
 * </ol>
 *
 * <p>SSH 隧道：Xray gRPC 通常 listen 127.0.0.1:10085，远端访问需要 SSH 本地端口转发。
 * 这块独立放 {@code com.nook.biz.xray.util.SshTunnel}(待加)，用 sshj 的 LocalPortForwarder 实现。
 */
@Slf4j
public class XrayGrpcBackend implements XrayBackend, AutoCloseable {

    private final ServerCredentialDTO cred;
    private final SshTunnelManager tunnelManager;
    private final ManagedChannel channel;
    private final int forwardedLocalPort;

    public XrayGrpcBackend(ServerCredentialDTO cred, SshTunnelManager tunnelManager) {
        if (StrUtil.isBlank(cred.xrayGrpcHost()) || ObjectUtil.isNull(cred.xrayGrpcPort())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        this.cred = cred;
        this.tunnelManager = tunnelManager;
        // 走 SSH 隧道: 把 nook-localhost:randomPort 转发到远端 cred.xrayGrpcHost:cred.xrayGrpcPort
        // 通常 cred.xrayGrpcHost = "127.0.0.1", 因为 Xray 只听本地 loopback (公网不暴露)
        SshTunnel tunnel = tunnelManager.ensureTunnel(cred, cred.xrayGrpcHost(), cred.xrayGrpcPort());
        this.forwardedLocalPort = tunnel.localPort();
        // gRPC channel 连本地转发端口, 实际 bytes 走 SSH 到远端
        this.channel = ManagedChannelBuilder.forAddress("127.0.0.1", forwardedLocalPort)
                .usePlaintext()
                .build();
        log.info("[grpc] backend 创建 server={} channel=127.0.0.1:{} → {}:{} (via ssh)",
                cred.serverId(), forwardedLocalPort, cred.xrayGrpcHost(), cred.xrayGrpcPort());
    }

    @Override
    public String serverId() {
        return cred.serverId();
    }

    /**
     * 触发 channel 主动建连并等待 READY；best-effort 探活。
     * READY 仅证明 TCP/TLS 通了，不证明对端是 Xray。
     * TODO(@team, 2026-06-30): proto 接入后改成调一次轻量 RPC 再判定，
     * 同时把 100ms 忙轮询换成 channel.notifyWhenStateChanged 回调。
     */
    @Override
    public void verifyConnectivity() {
        ConnectivityState state = channel.getState(true);
        long deadlineNs = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(cred.backendTimeoutSecondsOrDefault());
        while (state != ConnectivityState.READY && System.nanoTime() < deadlineNs) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            state = channel.getState(false);
        }
        if (state != ConnectivityState.READY) {
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, cred.serverId());
        }
    }

    @Override
    public List<XrayInboundInfo> listInbounds() {
        throw new BusinessException(XrayErrorCode.GRPC_NOT_IMPLEMENTED, "listInbounds");
    }

    @Override
    public void addClient(XrayClientSpec spec) {
        throw new BusinessException(XrayErrorCode.GRPC_NOT_IMPLEMENTED, "addClient");
    }

    @Override
    public void delClient(XrayClientRef ref) {
        throw new BusinessException(XrayErrorCode.GRPC_NOT_IMPLEMENTED, "delClient");
    }

    @Override
    public XrayClientTraffic getClientTraffic(XrayClientRef ref) {
        throw new BusinessException(XrayErrorCode.GRPC_NOT_IMPLEMENTED, "getClientTraffic");
    }

    @Override
    public void resetClientTraffic(XrayClientRef ref) {
        throw new BusinessException(XrayErrorCode.GRPC_NOT_IMPLEMENTED, "resetClientTraffic");
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
    }
}
