package com.nook.framework.ssh.internal;

import cn.hutool.core.util.StrUtil;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.config.SshSessionProperties;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshChannel;
import com.nook.framework.ssh.core.SshSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * SshSession 的 MINA 实现, 仅持有一条 ClientSession + ssh facet, 不知道任何业务.
 *
 * @author nook
 */
@Slf4j
class MinaSshSession implements SshSession {

    private final SessionCredential cred;
    private final ClientSession clientSession;
    private final SshChannel sshFacet;
    private final Instant connectedAt;
    /** shutdown 用 CAS 防双重 close (manager 主动 + MINA close 事件回调可能并发触发). */
    private final AtomicBoolean shutdownLatch = new AtomicBoolean(false);

    private MinaSshSession(SessionCredential cred, ClientSession clientSession) {
        this.cred = cred;
        this.clientSession = clientSession;
        this.sshFacet = new MinaSshChannel(cred, clientSession);
        this.connectedAt = Instant.now();
    }

    /**
     * 同步建会话; 任一步失败回滚已建资源并抛 SSH_UNREACHABLE.
     *
     * @param sshClient MINA 全局 SshClient
     * @param cred      SSH 凭据
     * @param props     SSH 会话基础参数
     * @param onClose   底层 SSH session 关闭时回调; manager 用它把 cache 里的死条目自动摘出
     * @return 已就绪的 MinaSshSession
     */
    static MinaSshSession build(SshClient sshClient,
                                SessionCredential cred,
                                SshSessionProperties props,
                                Consumer<MinaSshSession> onClose) {
        validateCred(cred);
        ClientSession session = null;
        try {
            session = openAndAuth(sshClient, cred, props.getConnectTimeout());
            log.info("[ssh-session] 建立成功 server={} {}@{}:{}",
                    cred.getServerId(), cred.getSshUser(), cred.getSshHost(), cred.getSshPort());
            MinaSshSession instance = new MinaSshSession(cred, session);
            instance.registerCloseListener(onClose);
            return instance;
        } catch (BusinessException be) {
            closeQuietly(session);
            throw be;
        } catch (Exception e) {
            closeQuietly(session);
            log.warn("[ssh-session] 建立失败 server={}: {}", cred.getServerId(), e.getMessage());
            throw new BusinessException(SshErrorCode.SSH_UNREACHABLE, e, cred.getServerId());
        }
    }

    /**
     * 把 onClose 注册到 MINA ClientSession 的 close future, 任何关闭路径都会触发.
     *
     * @param onClose 关闭回调
     */
    private void registerCloseListener(Consumer<MinaSshSession> onClose) {
        clientSession.addCloseFutureListener(future -> {
            try {
                onClose.accept(this);
            } catch (Exception e) {
                log.warn("[ssh-session] close 回调异常 server={}", cred.getServerId(), e);
            }
        });
    }

    @Override
    public String serverId() {
        return cred.getServerId();
    }

    @Override
    public SessionCredential cred() {
        return cred;
    }

    @Override
    public boolean isAlive() {
        return !shutdownLatch.get() && clientSession.isOpen();
    }

    @Override
    public SshChannel ssh() {
        return sshFacet;
    }

    /**
     * 会话首次建立时刻.
     *
     * @return Instant
     */
    Instant connectedAt() {
        return connectedAt;
    }

    /**
     * 释放底层资源; manager 调用, 幂等 (CAS 抢只让一个线程真正 close).
     */
    void shutdown() {
        if (!shutdownLatch.compareAndSet(false, true)) return;
        log.info("[ssh-session] 关闭 server={}", cred.getServerId());
        closeQuietly(clientSession);
    }

    // ===== 构造期辅助 =====

    /** 校验 SSH 必填字段. */
    private static void validateCred(SessionCredential cred) {
        if (StrUtil.isBlank(cred.getSshHost()) || StrUtil.isBlank(cred.getSshUser()) || StrUtil.isBlank(cred.getSshPassword())) {
            throw new BusinessException(SshErrorCode.SSH_CREDENTIAL_INVALID, cred.getServerId());
        }
    }

    /** TCP 连接 + 鉴权; 鉴权失败关掉 session 再向上抛, 防资源泄漏. */
    private static ClientSession openAndAuth(SshClient client, SessionCredential cred, Duration connectTimeout) throws IOException {
        ConnectFuture cf = client.connect(cred.getSshUser(), cred.getSshHost(), cred.getSshPort());
        cf.verify(connectTimeout.toMillis());
        ClientSession session = cf.getSession();
        try {
            session.addPasswordIdentity(cred.getSshPassword());
            session.auth().verify(connectTimeout.toMillis());
            return session;
        } catch (Exception e) {
            try { session.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    /** 静默关 ClientSession; 构造期回滚用, 不希望它把上层真正的失败原因覆盖掉. */
    private static void closeQuietly(ClientSession session) {
        if (session != null) {
            try { session.close(); } catch (IOException ignored) { }
        }
    }
}
