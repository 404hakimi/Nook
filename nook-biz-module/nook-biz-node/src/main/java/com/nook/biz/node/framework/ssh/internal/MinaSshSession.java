package com.nook.biz.node.framework.ssh.internal;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.PortForward;
import com.nook.biz.node.framework.ssh.SshChannel;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.config.SshSessionProperties;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    /** 已建立的端口转发缓存: 同一对 (host, port) 重复请求复用同一 forward, 避免反复走 SSH channel-open 协议 (~50-200ms). */
    private final ConcurrentMap<HostPort, MinaPortForward> forwardCache = new ConcurrentHashMap<>();

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

    @Override
    public PortForward openLocalForward(String remoteHost, int remotePort) {
        // 缓存复用: 同 (host, port) 第一次现建, 之后命中即返回; SSH channel-open 协议握手不重复走
        return forwardCache.computeIfAbsent(new HostPort(remoteHost, remotePort), k -> buildForward(k.host(), k.port()));
    }

    /** 现建 MINA 本地端口转发, 本地绑 0 让 OS 分配端口. */
    private MinaPortForward buildForward(String remoteHost, int remotePort) {
        try {
            // 本地端口绑 0 让 OS 分配; 返回的 tracker 里能拿到实际分配的端口
            SshdSocketAddress local = new SshdSocketAddress("127.0.0.1", 0);
            SshdSocketAddress remote = new SshdSocketAddress(remoteHost, remotePort);
            ExplicitPortForwardingTracker tracker = clientSession.createLocalPortForwardingTracker(local, remote);
            log.info("[ssh-session] 端口转发 server={} local=127.0.0.1:{} remote={}:{}",
                    cred.serverId(), tracker.getBoundAddress().getPort(), remoteHost, remotePort);
            return new MinaPortForward(tracker);
        } catch (IOException e) {
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, e, cred.serverId());
        }
    }

    private record HostPort(String host, int port) {
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
        if (StrUtil.isBlank(cred.sshHost()) || StrUtil.isBlank(cred.sshUser())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        if (StrUtil.isBlank(cred.sshPassword()) && StrUtil.isBlank(cred.sshPrivateKey())) {
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

    /** 按凭据类型挂上鉴权身份: 私钥优先, 否则用密码. */
    private static void attachIdentity(ClientSession session, ServerCredentialDTO cred) throws IOException {
        if (StrUtil.isNotBlank(cred.sshPrivateKey())) {
            session.addPublicKeyIdentity(loadKeyPair(cred.sshPrivateKey(), cred.sshPrivateKeyPassphrase()));
        } else {
            session.addPasswordIdentity(cred.sshPassword());
        }
    }

    /** PEM 字符串或文件路径都支持; MINA SSHD 不需要落临时文件即可加载, 比 sshj 干净. */
    private static KeyPair loadKeyPair(String keyMaterial, String passphrase) throws IOException {
        KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
        FilePasswordProvider pwd = StrUtil.isNotBlank(passphrase)
                ? FilePasswordProvider.of(passphrase)
                : FilePasswordProvider.EMPTY;
        Collection<KeyPair> kps;
        try {
            if (Files.exists(Path.of(keyMaterial))) {
                try (InputStream is = Files.newInputStream(Path.of(keyMaterial))) {
                    kps = loader.loadKeyPairs(null, NamedResource.ofName(keyMaterial), pwd, is);
                }
            } else {
                try (InputStream is = new ByteArrayInputStream(keyMaterial.getBytes(StandardCharsets.UTF_8))) {
                    kps = loader.loadKeyPairs(null, NamedResource.ofName("inline-pem"), pwd, is);
                }
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("解析 SSH 私钥失败", e);
        }
        if (kps == null || kps.isEmpty()) {
            throw new IOException("私钥文件未解析出任何 KeyPair");
        }
        return kps.iterator().next();
    }

    /** 静默关 ClientSession; 构造期回滚用, 不希望它把上层真正的失败原因覆盖掉. */
    private static void closeQuietly(ClientSession session) {
        if (session != null) {
            try { session.close(); } catch (IOException ignored) { }
        }
    }
}
