package com.nook.framework.ssh.internal;

import com.nook.framework.ssh.config.SshSessionProperties;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionManager;
import com.nook.framework.ssh.core.SshSessionScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * SshSessionManager 默认实现; 仅持有 MINA SshClient + props, 凭据由 caller 传入.
 * cache key = serverId:scope, 不同 scope 持有独立 ClientSession 互不影响.
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
class DefaultSshSessionManager implements SshSessionManager {

    private final SshClient sshClient;
    private final SshSessionProperties props;

    /** "serverId:scope" → SSH 会话; 死链通过 MINA close 回调被动摘出, 不跑后台健康检查. */
    private final ConcurrentMap<String, MinaSshSession> cache = new ConcurrentHashMap<>();

    /** 关闭钩子, 由 SshAutoConfiguration 通过 destroyMethod 触发, 释放所有会话. */
    void shutdown() {
        cache.values().forEach(MinaSshSession::shutdown);
        cache.clear();
        log.info("[ssh-manager] 关闭, 释放所有会话");
    }

    @Override
    public SshSession acquire(SessionCredential cred, SshSessionScope scope) {
        String key = cacheKey(cred.getServerId(), scope);
        MinaSshSession s = cache.get(key);
        if (s != null && s.isAlive()) return s;
        if (s != null) {
            // 缓存里的 session 已死, 摘出 + shutdown 走重建路径
            cache.remove(key, s);
            s.shutdown();
        }
        // computeIfAbsent 保证同 key 并发 acquire 只触发一次握手, 后到的线程等待复用
        return cache.computeIfAbsent(key, k -> buildOrThrow(cred, scope));
    }

    @Override
    public void invalidate(String serverId, SshSessionScope scope) {
        String key = cacheKey(serverId, scope);
        MinaSshSession removed = cache.remove(key);
        if (removed != null) {
            log.info("[ssh-manager] invalidate key={}", key);
            removed.shutdown();
        }
    }

    @Override
    public void invalidateAll(String serverId) {
        for (SshSessionScope scope : SshSessionScope.values()) {
            invalidate(serverId, scope);
        }
    }

    @Override
    public Map<String, SshSession.Snapshot> snapshot() {
        Map<String, SshSession.Snapshot> result = new HashMap<>(cache.size());
        cache.forEach((key, s) -> result.put(key, new SshSession.Snapshot(s.serverId(), s.connectedAt())));
        return Collections.unmodifiableMap(result);
    }

    @Override
    public <T> T runAdHoc(SessionCredential cred, Function<SshSession, T> action) {
        // ad-hoc session 不进 cache, 也不订阅 close 事件 (反正马上就 shutdown)
        MinaSshSession session = MinaSshSession.build(sshClient, cred, props, ignored -> {});
        try {
            return action.apply(session);
        } finally {
            session.shutdown();
        }
    }

    /** computeIfAbsent 的 mapping function; 抛异常时 cache 不会写入. */
    private MinaSshSession buildOrThrow(SessionCredential cred, SshSessionScope scope) {
        String key = cacheKey(cred.getServerId(), scope);
        return MinaSshSession.build(sshClient, cred, props, s -> onSessionClosed(key, s));
    }

    /** MINA close 事件回调; 用 cacheKey 摘出对应条目, 保证仅影响该 scope 的 session. */
    private void onSessionClosed(String key, MinaSshSession s) {
        // remove(K, V) 条件 remove 保证幂等, 也防止误删别的线程为同 key 刚建好的新 session
        if (cache.remove(key, s)) {
            log.info("[ssh-manager] session 自动失效 (close 事件) key={}", key);
        }
    }

    /** cache key 拼接; serverId + scope 两段, 避免 serverId 里带冒号也能正常区分. */
    private static String cacheKey(String serverId, SshSessionScope scope) {
        return serverId + ":" + scope.name();
    }
}
