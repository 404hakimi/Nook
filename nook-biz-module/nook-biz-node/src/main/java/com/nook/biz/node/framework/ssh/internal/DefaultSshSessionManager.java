package com.nook.biz.node.framework.ssh.internal;

import jakarta.annotation.Resource;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.api.event.ServerCredentialChangedEvent;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.ssh.config.SshSessionProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 远程 SSH 会话注册表.
 *
 * @author nook
 */
@Slf4j
@Component
public class DefaultSshSessionManager implements SshSessionManager {

    @Resource
    private SshClient sshClient;

    @Resource
    private ResourceServerApi resourceServerApi;

    @Resource
    private SshSessionProperties props;

    /** serverId → SSH 会话; 死链通过 MINA close 回调被动摘出, 不跑后台健康检查. */
    private final ConcurrentMap<String, MinaSshSession> cache = new ConcurrentHashMap<>();

    /** Spring 关闭钩子, 释放所有会话. */
    @PreDestroy
    void shutdown() {
        cache.values().forEach(MinaSshSession::shutdown);
        cache.clear();
        log.info("[ssh-manager] 关闭, 释放所有会话");
    }

    /**
     * 获取一个就绪 SSH 会话, 不存在或已死则现场建.
     *
     * @param serverId 目标主机 id
     * @return SshSession
     */
    @Override
    public SshSession acquire(String serverId) {
        MinaSshSession s = cache.get(serverId);
        if (s != null && s.isAlive()) return s;
        if (s != null) {
            // 缓存里的 session 已死 (网络断 / sshd 踢人 / 心跳超时), 摘出 + shutdown 走重建路径
            cache.remove(serverId, s);
            s.shutdown();
        }
        // computeIfAbsent 保证同 serverId 并发 acquire 只触发一次握手, 后到的线程等待复用
        return cache.computeIfAbsent(serverId, this::buildOrThrow);
    }

    /**
     * 主动失效 server 的缓存 session, 下次 acquire 用最新凭据重建.
     *
     * @param serverId 目标主机 id
     */
    @Override
    public void invalidate(String serverId) {
        MinaSshSession removed = cache.remove(serverId);
        if (removed != null) {
            log.info("[ssh-manager] invalidate server={}", serverId);
            removed.shutdown();
        }
    }

    /**
     * 全量 session 状态快照, 给运维查询用.
     *
     * @return Map (serverId → Snapshot)
     */
    @Override
    public Map<String, SshSession.Snapshot> snapshot() {
        Map<String, SshSession.Snapshot> result = new HashMap<>(cache.size());
        cache.forEach((id, s) -> result.put(id, new SshSession.Snapshot(id, s.connectedAt())));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 用临时凭据 (不来自 resource_server 表) 跑一次性操作, 不入缓存, 跑完即关.
     *
     * @param cred   一次性凭据
     * @param action 业务回调
     * @param <T>    返回类型
     * @return action 的返回值
     */
    @Override
    public <T> T runAdHoc(ServerCredentialDTO cred, Function<SshSession, T> action) {
        // ad-hoc session 不进 cache, 也不订阅 close 事件 (反正马上就 shutdown)
        MinaSshSession session = MinaSshSession.build(sshClient, cred, props, ignored -> {});
        try {
            return action.apply(session);
        } finally {
            session.shutdown();
        }
    }

    /**
     * 凭据变更事件: 失效老 session, 下次 acquire 用新凭据重建.
     *
     * @param event 凭据变更事件
     */
    @EventListener
    public void onCredentialChanged(ServerCredentialChangedEvent event) {
        log.info("[ssh-manager] 收到 ServerCredentialChangedEvent server={}", event.getServerId());
        invalidate(event.getServerId());
    }

    /**
     * computeIfAbsent 的 mapping function; 抛异常时 cache 不会写入.
     *
     * @param serverId 目标主机 id
     * @return MinaSshSession
     */
    private MinaSshSession buildOrThrow(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        // 注册 close 回调: MINA 任意原因关 ClientSession 时, onSessionClosed 会把死条目从 cache 摘出
        return MinaSshSession.build(sshClient, cred, props, this::onSessionClosed);
    }

    /**
     * MINA close 事件回调; 把 cache 里的对应实例摘出.
     *
     * @param s 已被 MINA 关闭的 session
     */
    private void onSessionClosed(MinaSshSession s) {
        // 用 remove(K, V) 条件 remove 保证幂等: 我们自己 shutdown 时已先 remove(K), 这里 remove(K, V)
        // 返 false 即 no-op; 同时也防止误删别的线程为同 serverId 刚建好的新 session
        if (cache.remove(s.serverId(), s)) {
            log.info("[ssh-manager] session 自动失效 (close 事件) server={}", s.serverId());
        }
    }
}
