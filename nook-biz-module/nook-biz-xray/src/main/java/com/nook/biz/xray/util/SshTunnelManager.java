package com.nook.biz.xray.util;

import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.api.event.ServerCredentialChangedEvent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 serverId 维护 SSH 隧道, 复用同一条 SSH 连接给多次 gRPC 调用.
 * 凭据变更时通过 ServerCredentialChangedEvent 自动失效, 与 XrayBackendFactory 同款机制.
 */
@Slf4j
@Component
public class SshTunnelManager {

    private final ConcurrentMap<String, SshTunnel> tunnels = new ConcurrentHashMap<>();

    /**
     * 获取/建立到指定 server 的 SSH 隧道.
     * remoteHost/remotePort 由调用方决定(通常是 cred.xrayGrpcHost/Port).
     * 已存在但 isAlive=false 时自动重建.
     */
    public SshTunnel ensureTunnel(ServerCredentialDTO cred, String remoteHost, int remotePort) {
        return tunnels.compute(cred.serverId(), (id, existing) -> {
            if (existing != null && existing.isAlive()) {
                return existing;
            }
            if (existing != null) {
                log.info("发现失效隧道 server={}, 重建", id);
                existing.close();
            }
            return SshTunnel.open(cred, remoteHost, remotePort);
        });
    }

    public void invalidate(String serverId) {
        SshTunnel old = tunnels.remove(serverId);
        if (old != null) old.close();
    }

    @EventListener
    public void onServerCredentialChanged(ServerCredentialChangedEvent event) {
        log.info("凭据变更, 关闭 SSH 隧道 server={}", event.serverId());
        invalidate(event.serverId());
    }

    @PreDestroy
    public void shutdown() {
        log.info("关闭所有 SSH 隧道, 共 {} 条", tunnels.size());
        tunnels.values().forEach(SshTunnel::close);
        tunnels.clear();
    }
}
