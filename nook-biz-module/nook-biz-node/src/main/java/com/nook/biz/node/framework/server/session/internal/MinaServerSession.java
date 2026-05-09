package com.nook.biz.node.framework.server.session.internal;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.server.session.ServerSession;
import com.nook.biz.node.framework.server.session.config.ServerSessionProperties;
import com.nook.biz.node.framework.ssh.SshChannel;
import com.nook.biz.node.framework.ssh.dto.SshExecResult;
import com.nook.biz.node.framework.ssh.internal.MinaSshChannel;
import com.nook.biz.node.framework.xray.inbound.grpc.InboundGrpcClient;
import com.nook.biz.node.framework.xray.inbound.grpc.InboundGrpcClientImpl;
import com.nook.biz.node.framework.xray.stats.GrpcXrayStatsClient;
import com.nook.biz.node.framework.xray.stats.XrayStatsClient;
import com.nook.common.web.exception.BusinessException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** ServerSession 实现; 持有底层资源 (SSH + gRPC channel) + 三个 facet 实例, 仅管会话生命周期. */
@Slf4j
class MinaServerSession implements ServerSession {

    private final ServerCredentialDTO cred;
    private final ClientSession clientSession;
    private final ExplicitPortForwardingTracker forwardTracker;
    private final ManagedChannel grpcChannel;

    // ===== 三个 facet 实例 (构造时一次性建好, 调用方通过 ssh()/stats()/inbound() 拿) =====
    private final SshChannel sshFacet;
    private final XrayStatsClient statsFacet;
    private final InboundGrpcClient inboundFacet;

    private final Instant connectedAt;
    private volatile State state;
    private volatile Instant lastUsedAt;
    private volatile Instant lastPingAt;
    private volatile String lastError;
    private final AtomicInteger pingFailCount = new AtomicInteger(0);

    private MinaServerSession(ServerCredentialDTO cred,
                              ClientSession clientSession,
                              ExplicitPortForwardingTracker forwardTracker,
                              ManagedChannel grpcChannel) {
        this.cred = cred;
        this.clientSession = clientSession;
        this.forwardTracker = forwardTracker;
        this.grpcChannel = grpcChannel;
        this.sshFacet = new MinaSshChannel(cred.serverId(), clientSession);
        this.statsFacet = new GrpcXrayStatsClient(cred, grpcChannel);
        this.inboundFacet = new InboundGrpcClientImpl(cred, grpcChannel);
        this.connectedAt = Instant.now();
        this.lastUsedAt = this.connectedAt;
        this.state = State.READY;
    }

    /** 同步建会话; 任一步失败回滚已建资源并抛 BACKEND_UNREACHABLE. */
    static MinaServerSession build(SshClient sshClient,
                                   ServerCredentialDTO cred,
                                   ServerSessionProperties props) {
        validateCred(cred);
        ClientSession session = null;
        ExplicitPortForwardingTracker tracker = null;
        ManagedChannel channel = null;
        try {
            session = openAndAuth(sshClient, cred, props.getConnectTimeout());
            tracker = openForward(session, cred);
            channel = ManagedChannelBuilder
                    .forAddress("127.0.0.1", tracker.getBoundAddress().getPort())
                    .usePlaintext()
                    .build();
            log.info("[session] 建立成功 server={} local=127.0.0.1:{} remote={}:{}",
                    cred.serverId(), tracker.getBoundAddress().getPort(),
                    cred.xrayGrpcHost(), cred.xrayGrpcPort());
            return new MinaServerSession(cred, session, tracker, channel);
        } catch (BusinessException be) {
            closeQuietly(channel, tracker, session);
            throw be;
        } catch (Exception e) {
            closeQuietly(channel, tracker, session);
            log.warn("[session] 建立失败 server={}: {}", cred.serverId(), e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, e, cred.serverId());
        }
    }

    @Override
    public String serverId() {
        return cred.serverId();
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public SshChannel ssh() {
        return sshFacet;
    }

    @Override
    public XrayStatsClient stats() {
        return statsFacet;
    }

    @Override
    public InboundGrpcClient inbound() {
        return inboundFacet;
    }

    @Override
    public boolean ping() {
        if (state != State.READY || !clientSession.isOpen()) return false;
        try {
            // 走 sshFacet 跑 "true" 命令; 比纯 SSH 协议层心跳更端到端 (能发现"SSH 通道还在但跑不了命令")
            SshExecResult r = sshFacet.exec("true", Duration.ofSeconds(5));
            lastPingAt = Instant.now();
            pingFailCount.set(0);
            return r.exitCode() == 0;
        } catch (Exception e) {
            int fails = pingFailCount.incrementAndGet();
            lastError = e.getMessage();
            log.debug("[session] ping 失败 server={} fails={}: {}", cred.serverId(), fails, e.getMessage());
            return false;
        }
    }

    // ===== package-private 状态访问 (manager 用) =====

    int pingFailCount() {
        return pingFailCount.get();
    }

    Instant connectedAt() {
        return connectedAt;
    }

    Instant lastUsedAt() {
        return lastUsedAt;
    }

    Instant lastPingAt() {
        return lastPingAt;
    }

    String lastError() {
        return lastError;
    }

    void touchLastUsed() {
        lastUsedAt = Instant.now();
    }

    /** manager 调用; 设 DEAD + 释放底层资源, 幂等. */
    void shutdown() {
        if (state == State.DEAD) return;
        state = State.DEAD;
        log.info("[session] 关闭 server={}", cred.serverId());
        closeQuietly(grpcChannel, forwardTracker, clientSession);
    }

    // ===== 构造期辅助 =====

    private static void validateCred(ServerCredentialDTO cred) {
        if (StrUtil.isBlank(cred.sshHost()) || StrUtil.isBlank(cred.sshUser())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        if (StrUtil.isBlank(cred.sshPassword()) && StrUtil.isBlank(cred.sshPrivateKey())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        if (StrUtil.isBlank(cred.xrayGrpcHost())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
    }

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

    private static void attachIdentity(ClientSession session, ServerCredentialDTO cred) throws IOException {
        if (StrUtil.isNotBlank(cred.sshPrivateKey())) {
            session.addPublicKeyIdentity(loadKeyPair(cred.sshPrivateKey(), cred.sshPrivateKeyPassphrase()));
        } else {
            session.addPasswordIdentity(cred.sshPassword());
        }
    }

    /** PEM 字符串或文件路径都支持; MINA SSHD 比 sshj 优势在不需要写临时文件. */
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

    private static ExplicitPortForwardingTracker openForward(ClientSession session, ServerCredentialDTO cred) throws IOException {
        // 本地端口绑 0 让 OS 分配; 返回的 ExplicitPortForwardingTracker 里能拿到实际分配的端口
        SshdSocketAddress local = new SshdSocketAddress("127.0.0.1", 0);
        SshdSocketAddress remote = new SshdSocketAddress(cred.xrayGrpcHost(), cred.xrayGrpcPort());
        return session.createLocalPortForwardingTracker(local, remote);
    }

    private static void closeQuietly(ManagedChannel channel,
                                     ExplicitPortForwardingTracker tracker,
                                     ClientSession session) {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (tracker != null) {
            try { tracker.close(); } catch (IOException ignored) { }
        }
        if (session != null) {
            try { session.close(); } catch (IOException ignored) { }
        }
    }
}
