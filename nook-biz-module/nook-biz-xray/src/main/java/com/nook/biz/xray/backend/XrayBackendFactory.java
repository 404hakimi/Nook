package com.nook.biz.xray.backend;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.api.event.ServerCredentialChangedEvent;
import com.nook.biz.xray.backend.grpc.XrayGrpcBackend;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.biz.xray.util.SshExecutor;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 按 serverId 缓存 backend; 每次 get() 探活, 死了重建。
 *
 * <p>SSH 隧道是有状态资源: 长闲置 / 远端 sshd 断开 / 中间 NAT 老化都会让隧道死掉,
 * 但 nook 这边 channel 仍指向旧本地端口造成 UNAVAILABLE。本工厂用 isAlive 探测在分发前自愈,
 * 让上层调用拿到的总是可用 backend。
 *
 * <p>失效触发点:
 * <ul>
 *   <li>{@link #get} 时 isAlive() 返回 false (隧道断 / channel 关闭)</li>
 *   <li>{@link #onServerCredentialChanged} 服务器凭据更新事件 — 必须丢老 backend 重新认证</li>
 *   <li>{@link #markDead} 调用方在 RPC 收到 UNAVAILABLE 时主动通报, 让下次 get 强制重建</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XrayBackendFactory {

    private final SshExecutor sshExecutor;
    private final ConcurrentMap<String, XrayGrpcBackend> cache = new ConcurrentHashMap<>();

    /** 拿/创建该 server 对应的 backend; 缓存的若不健康会被丢弃并重建。 */
    public XrayBackend get(ServerCredentialDTO cred) {
        if (ObjectUtil.isNull(cred)) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, "<null>");
        }
        XrayGrpcBackend cached = cache.get(cred.serverId());
        if (cached != null && cached.isAlive()) {
            return cached;
        }
        if (cached != null) {
            log.info("[backend] 缓存 backend 已不健康, 重建 server={}", cred.serverId());
            invalidate(cred.serverId());
        }
        // computeIfAbsent 保证并发下只创建一次, 但失败时不会留半残实例
        return cache.computeIfAbsent(cred.serverId(),
                id -> new XrayGrpcBackend(cred, sshExecutor));
    }

    /** RPC 收到不可恢复错误后调; 让下次 get 强制重建 backend (含重新建 SSH 隧道)。 */
    public void markDead(String serverId) {
        log.info("[backend] markDead server={}", serverId);
        invalidate(serverId);
    }

    /**
     * 包一层"瞬时不可达自愈"重试: backend 抛 BACKEND_UNREACHABLE 时 markDead + 重建 + 重试一次。
     * 适用所有走 backend 的写操作 (addClient/delClient/getStats/...); 重试只 1 次, 避免雪崩。
     */
    public <T> T invoke(ServerCredentialDTO cred, Function<XrayBackend, T> op) {
        try {
            return op.apply(get(cred));
        } catch (BusinessException be) {
            if (be.getCode() != XrayErrorCode.BACKEND_UNREACHABLE.getCode()) throw be;
            log.warn("[backend] BACKEND_UNREACHABLE, markDead 后重试 server={}", cred.serverId());
            markDead(cred.serverId());
            return op.apply(get(cred));
        }
    }

    /** void 版本; addClient / delClient / resetTraffic 等无返回值的操作用。 */
    public void invokeVoid(ServerCredentialDTO cred, Consumer<XrayBackend> op) {
        invoke(cred, b -> {
            op.accept(b);
            return null;
        });
    }

    /** 让指定 server 的缓存失效; 下次 get 会重建。 */
    public void invalidate(String serverId) {
        XrayGrpcBackend old = cache.remove(serverId);
        closeQuietly(old);
    }

    /** 监听 resource 模块的服务器变更事件, 自动失效缓存。 */
    @EventListener
    public void onServerCredentialChanged(ServerCredentialChangedEvent event) {
        log.info("[backend] 收到 ServerCredentialChangedEvent server={}, 失效 backend 缓存", event.serverId());
        invalidate(event.serverId());
    }

    /** 全量清空; 测试 / JVM 退出时用。 */
    public void invalidateAll() {
        cache.values().forEach(this::closeQuietly);
        cache.clear();
    }

    @PreDestroy
    public void shutdown() {
        invalidateAll();
    }

    private void closeQuietly(XrayGrpcBackend backend) {
        if (backend == null) return;
        try {
            backend.close();
        } catch (Exception e) {
            log.warn("[backend] 关闭失败 server={}", backend.serverId(), e);
        }
    }
}
