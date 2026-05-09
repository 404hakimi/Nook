package com.nook.biz.node.framework.server.session.internal;

import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.api.event.ServerCredentialChangedEvent;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.server.session.ServerSession;
import com.nook.biz.node.framework.server.session.ServerSessionManager;
import com.nook.biz.node.framework.server.session.config.ServerSessionProperties;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultServerSessionManager implements ServerSessionManager {

    private final SshClient sshClient;
    private final ResourceServerApi resourceServerApi;
    private final ServerSessionProperties props;

    /**
     * cache value 是 future 而非 session 直接放: 让并发 acquire 同一 serverId 共享一次握手,
     * 避免重复建会话; 失败时 future remove 让下次 acquire 重新构造.
     */
    private final ConcurrentMap<String, CompletableFuture<MinaServerSession>> cache = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    @PostConstruct
    void start() {
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "xray-server-session-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::runHealthCheck,
                props.getHealthCheckInterval().toMillis(),
                props.getHealthCheckInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::runIdleCleanup,
                props.getCleanupInterval().toMillis(),
                props.getCleanupInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        log.info("[manager] 启动 healthCheck={} cleanup={} idleTimeout={}",
                props.getHealthCheckInterval(), props.getCleanupInterval(), props.getIdleTimeout());
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        cache.values().forEach(this::shutdownIfDone);
        cache.clear();
        log.info("[manager] 关闭, 释放所有会话");
    }

    @Override
    public ServerSession acquire(String serverId) {
        // 主路径: 命中已 READY 直接返回 (零开销)
        CompletableFuture<MinaServerSession> existing = cache.get(serverId);
        if (existing != null) {
            MinaServerSession s = sessionIfReady(existing);
            if (s != null) {
                s.touchLastUsed();
                return s;
            }
            // 已 done 但 DEAD / 异常: 清掉重建
            if (existing.isDone()) {
                cache.remove(serverId, existing);
                shutdownIfDone(existing);
            }
        }

        // 抢占创建权; 若被别的线程抢先, 复用其 future 等待
        CompletableFuture<MinaServerSession> placeholder = new CompletableFuture<>();
        CompletableFuture<MinaServerSession> winner = cache.putIfAbsent(serverId, placeholder);
        if (winner != null) {
            return waitFor(winner, serverId);
        }

        try {
            ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
            MinaServerSession session = MinaServerSession.build(sshClient, cred, props);
            placeholder.complete(session);
            return session;
        } catch (BusinessException be) {
            cache.remove(serverId, placeholder);
            placeholder.completeExceptionally(be);
            throw be;
        } catch (RuntimeException e) {
            cache.remove(serverId, placeholder);
            placeholder.completeExceptionally(e);
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, e, serverId);
        }
    }

    @Override
    public void invalidate(String serverId) {
        CompletableFuture<MinaServerSession> removed = cache.remove(serverId);
        if (removed != null) {
            log.info("[manager] invalidate server={}", serverId);
            shutdownIfDone(removed);
        }
    }

    @Override
    public Map<String, ServerSession.Snapshot> snapshot() {
        Map<String, ServerSession.Snapshot> result = new HashMap<>(cache.size());
        cache.forEach((id, future) -> {
            if (!future.isDone()) {
                result.put(id, new ServerSession.Snapshot(id, ServerSession.State.CONNECTING,
                        null, null, null, 0, null));
                return;
            }
            MinaServerSession s = future.getNow(null);
            if (s == null) return;
            result.put(id, new ServerSession.Snapshot(id, s.state(),
                    s.connectedAt(), s.lastUsedAt(), s.lastPingAt(), s.pingFailCount(), s.lastError()));
        });
        return Collections.unmodifiableMap(result);
    }

    @Override
    public <T> T runAdHoc(ServerCredentialDTO cred, Function<ServerSession, T> action) {
        // 不入 cache; 跑完即关. 用于 IP 池一键部署 SOCKS5 等临时主机操作.
        MinaServerSession session = MinaServerSession.build(sshClient, cred, props);
        try {
            return action.apply(session);
        } finally {
            session.shutdown();
        }
    }

    /** 凭据更新即丢老 session, 下次 acquire 用新凭据重建. */
    @EventListener
    public void onCredentialChanged(ServerCredentialChangedEvent event) {
        log.info("[manager] 收到 ServerCredentialChangedEvent server={}", event.serverId());
        invalidate(event.serverId());
    }

    // ===== 后台任务 =====

    private void runHealthCheck() {
        cache.forEach((id, future) -> {
            MinaServerSession s = sessionIfReady(future);
            if (s == null) return;
            try {
                if (s.ping()) return;
                if (s.pingFailCount() >= props.getHealthFailThreshold()) {
                    log.warn("[manager] healthCheck DEAD server={} fails={}", id, s.pingFailCount());
                    cache.remove(id, future);
                    s.shutdown();
                }
            } catch (Exception e) {
                // ping 自身炸了不动 session, 避免管理器自杀
                log.warn("[manager] healthCheck 异常 server={}", id, e);
            }
        });
    }

    private void runIdleCleanup() {
        Instant threshold = Instant.now().minus(props.getIdleTimeout());
        cache.forEach((id, future) -> {
            MinaServerSession s = sessionIfReady(future);
            if (s == null) return;
            if (s.lastUsedAt().isBefore(threshold)) {
                log.info("[manager] idleCleanup 关闭 server={} lastUsed={}", id, s.lastUsedAt());
                cache.remove(id, future);
                s.shutdown();
            }
        });
    }

    // ===== 工具 =====

    /** future 已 done 且会话 READY 才返回, 否则 null. */
    private static MinaServerSession sessionIfReady(CompletableFuture<MinaServerSession> future) {
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) return null;
        MinaServerSession s = future.getNow(null);
        if (s == null || s.state() != ServerSession.State.READY) return null;
        return s;
    }

    private ServerSession waitFor(CompletableFuture<MinaServerSession> future, String serverId) {
        try {
            MinaServerSession s = future.get(props.getAcquireTimeout().toMillis(), TimeUnit.MILLISECONDS);
            s.touchLastUsed();
            return s;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException be) throw be;
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, cause, serverId);
        } catch (TimeoutException e) {
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, e, serverId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, e, serverId);
        }
    }

    private void shutdownIfDone(CompletableFuture<MinaServerSession> future) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
            MinaServerSession s = future.getNow(null);
            if (s != null) s.shutdown();
        }
    }
}
