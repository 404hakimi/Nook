package com.nook.biz.xray.backend;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.api.event.ServerCredentialChangedEvent;
import com.nook.biz.xray.backend.grpc.XrayGrpcBackend;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.biz.xray.util.SshTunnelManager;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 server 拿 backend 实例。第一次访问懒创建并缓存；同 server 后续调用复用同一对象，
 * 让 XrayGrpcBackend 的 channel 不被反复重建。
 * 凭据更新后由 ServerCredentialChangedEvent 自动失效缓存(SshTunnelManager 也监听同一事件).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XrayBackendFactory {

    private final SshTunnelManager sshTunnelManager;
    private final ConcurrentMap<String, XrayBackend> cache = new ConcurrentHashMap<>();

    /** 拿/创建该 server 对应的 backend。线程安全；同一 server 高并发只会创建一次。 */
    public XrayBackend get(ServerCredentialDTO cred) {
        if (ObjectUtil.isNull(cred)) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, "<null>");
        }
        return cache.computeIfAbsent(cred.serverId(), id -> new XrayGrpcBackend(cred, sshTunnelManager));
    }

    /** 让指定 server 的缓存失效；下次 get 会重建。常见调用点：服务器凭据更新后。 */
    public void invalidate(String serverId) {
        XrayBackend old = cache.remove(serverId);
        closeQuietly(old);
    }

    /** 监听 resource 模块的 server 变更事件，自动失效缓存。 */
    @EventListener
    public void onServerCredentialChanged(ServerCredentialChangedEvent event) {
        log.info("收到 ServerCredentialChangedEvent server={}, 失效 backend 缓存", event.serverId());
        invalidate(event.serverId());
    }

    /** 全量清空——一般在测试/JVM 退出时用。 */
    public void invalidateAll() {
        cache.values().forEach(this::closeQuietly);
        cache.clear();
    }

    @PreDestroy
    public void shutdown() {
        invalidateAll();
    }

    private void closeQuietly(XrayBackend backend) {
        if (backend instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception e) {
                log.warn("关闭 backend 失败 server={}: {}", backend.serverId(), e.getMessage());
            }
        }
    }
}
