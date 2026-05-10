package com.nook.biz.node.framework.ssh.internal;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.SshChannel;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.config.SshSessionProperties;
import com.nook.common.web.exception.BusinessException;
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
 * SshSession 的 MINA 实现, 仅持有一条 ClientSession + ssh facet, 不知道任何 gRPC / xray.
 *
 * @author nook
 */
@Slf4j
class MinaSshSession implements SshSession {

    private final ServerCredentialDTO cred;
    private final ClientSession clientSession;
    private final SshChannel sshFacet;
    private final Instant connectedAt;
    /** shutdown 用 CAS 防双重 close (manager 主动 + MINA close 事件回调可能并发触发). */
    private final AtomicBoolean shutdownLatch = new AtomicBoolean(false);

    private MinaSshSession(ServerCredentialDTO cred, ClientSession clientSession) {
        this.cred = cred;
        this.clientSession = clientSession;
        this.sshFacet = new MinaSshChannel(cred.serverId(), clientSession);
        this.connectedAt = Instant.now();
    }

    /**
     * 同步建会话, 任一步失败回滚已建资源并抛 BACKEND_UNREACHABLE.
     *
     * @param sshClient MINA 全局 SshClient
     * @param cred      SSH 凭据
     * @param props     SSH 会话基础参数
     * @param onClose   MINA 检测到底层 SSH session 关闭时回调; manager 用它把 cache 里的死条目自动摘出
     * @return MinaSshSession
     */
    static MinaSshSession build(SshClient sshClient,
                                ServerCredentialDTO cred,
                                SshSessionProperties props,
                                Consumer<MinaSshSession> onClose) {
        validateCred(cred);
        ClientSession session = null;
        try {
            session = openAndAuth(sshClient, cred, props.getConnectTimeout());
            log.info("[ssh-session] 建立成功 server={} {}@{}:{}",
                    cred.serverId(), cred.sshUser(), cred.sshHost(), cred.sshPort());
            MinaSshSession instance = new MinaSshSession(cred, session);
            instance.registerCloseListener(onClose);
            return instance;
        } catch (BusinessException be) {
            closeQuietly(session);
            throw be;
        } catch (Exception e) {
            closeQuietly(session);
            log.warn("[ssh-session] 建立失败 server={}: {}", cred.serverId(), e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, e, cred.serverId());
        }
    }

    /** 把 onClose 注册到 MINA ClientSession 的 close future, 任何关闭路径都会触发. */
    private void registerCloseListener(Consumer<MinaSshSession> onClose) {
        clientSession.addCloseFutureListener(future -> {
            try {
                onClose.accept(this);
            } catch (Exception e) {
                log.warn("[ssh-session] close 回调异常 server={}", cred.serverId(), e);
            }
        });
    }

    @Override
    public String serverId() {
        return cred.serverId();
    }

    @Override
    public boolean isAlive() {
        return !shutdownLatch.get() && clientSession.isOpen();
    }

    @Override
    public SshChannel ssh() {
        return sshFacet;
    }

    Instant connectedAt() {
        return connectedAt;
    }

    /** manager 调用; 释放底层资源, 幂等 (CAS 抢只让一个线程真正 close). */
    void shutdown() {
        if (!shutdownLatch.compareAndSet(false, true)) return;
        log.info("[ssh-session] 关闭 server={}", cred.serverId());
        closeQuietly(clientSession);
    }

    // ===== 构造期辅助 =====

    /** 校验 SSH 必填字段; xrayGrpcHost 不在这层校验, 那是 Xray 域的事. */
    private static void validateCred(ServerCredentialDTO cred) {
        if (StrUtil.isBlank(cred.sshHost()) || StrUtil.isBlank(cred.sshUser()) || StrUtil.isBlank(cred.sshPassword())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        // 注意: xrayGrpcHost 不在 SSH 层校验; 那是 Xray 域 (XrayGrpcChannelManager) 的事
    }

    /** TCP 连接 + 鉴权; 鉴权失败关掉 session 再向上抛, 防资源泄漏. */
    private static ClientSession openAndAuth(SshClient client, ServerCredentialDTO cred, Duration connectTimeout) throws IOException {
        ConnectFuture cf = client.connect(cred.sshUser(), cred.sshHost(), cred.sshPort());
        cf.verify(connectTimeout.toMillis());
        ClientSession session = cf.getSession();
        try {
            attachIdentity(session, cred);
            session.auth().verify(connectTimeout.toMillis());
            return session;
        } catch (Exception e) {
            try { session.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    /** 用密码鉴权挂身份. */
    private static void attachIdentity(ClientSession session, ServerCredentialDTO cred) {
        session.addPasswordIdentity(cred.sshPassword());
    }

    /** 静默关 ClientSession; 构造期回滚用, 不希望它把上层真正的失败原因覆盖掉. */
    private static void closeQuietly(ClientSession session) {
        if (session != null) {
            try { session.close(); } catch (IOException ignored) { }
        }
    }
}
