package com.nook.biz.xray.util;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 单条 SSH 本地端口转发隧道：
 *   nook-localhost:{localPort}  ──ssh──>  remoteHost:remotePort (从远端视角)
 * 通常 remoteHost = "127.0.0.1", remotePort = Xray gRPC 端口(62789).
 *
 * <p>典型用法:
 * <pre>{@code
 * try (SshTunnel t = SshTunnel.open(cred, "127.0.0.1", 62789)) {
 *     ManagedChannel ch = ManagedChannelBuilder
 *         .forAddress("127.0.0.1", t.localPort())
 *         .usePlaintext().build();
 *     // ... gRPC ops
 * }
 * }</pre>
 *
 * <p>线程模型: 内部启 daemon 线程跑 LocalPortForwarder.listen() 阻塞接受连接;
 * close() 时关 ServerSocket 让 listen() 抛异常, daemon 线程自然退出.
 */
@Slf4j
public class SshTunnel implements AutoCloseable {

    /** SSH 建连超时 */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    /** SSH 会话保活间隔(秒); 不设的话长闲置会被中间网络设备 reset */
    private static final int KEEPALIVE_INTERVAL_SECONDS = 30;

    private final SSHClient ssh;
    private final ServerSocket localSocket;
    private final int localPort;
    private final Thread acceptThread;
    private final String serverId;
    private volatile boolean closed = false;

    private SshTunnel(SSHClient ssh, ServerSocket localSocket, Thread acceptThread, String serverId) {
        this.ssh = ssh;
        this.localSocket = localSocket;
        this.localPort = localSocket.getLocalPort();
        this.acceptThread = acceptThread;
        this.serverId = serverId;
    }

    public int localPort() {
        return localPort;
    }

    public boolean isAlive() {
        return !closed && ssh.isConnected() && !localSocket.isClosed();
    }

    /**
     * 打开一条 SSH 隧道并立即开始接受本地连接转发.
     * 同步等到 ServerSocket bind 完成、listen() 线程启动后才返回.
     */
    public static SshTunnel open(ServerCredentialDTO cred, String remoteHost, int remotePort) {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SECONDS));
        // setTimeout 是 socket read 超时, 不影响保活的"心跳间隔"
        ssh.setTimeout((int) TimeUnit.SECONDS.toMillis(60));

        try {
            ssh.connect(cred.sshHost(), cred.sshPort());
            authenticate(ssh, cred);
            // 启用 keepalive: 每 30s 发一个心跳包, 避免长闲置被 NAT/防火墙断开
            ssh.getConnection().getKeepAlive().setKeepAliveInterval(KEEPALIVE_INTERVAL_SECONDS);

            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress("127.0.0.1", 0)); // 0 = 让 OS 选个空闲端口
            int localPort = ss.getLocalPort();

            Parameters params = new Parameters("127.0.0.1", localPort, remoteHost, remotePort);
            LocalPortForwarder forwarder = ssh.newLocalPortForwarder(params, ss);

            Thread t = new Thread(() -> {
                try {
                    forwarder.listen();
                } catch (IOException e) {
                    // 关 socket 时 listen() 会抛, 属正常退出; 其它情况 log 一下
                    if (!ss.isClosed()) {
                        log.warn("SSH 隧道 listen() 异常退出 server={}: {}",
                                cred.serverId(), e.getMessage());
                    }
                }
            }, "ssh-tunnel-" + cred.serverId());
            t.setDaemon(true);
            t.start();

            log.info("SSH 隧道已建立 server={} local=127.0.0.1:{} remote={}:{}",
                    cred.serverId(), localPort, remoteHost, remotePort);
            return new SshTunnel(ssh, ss, t, cred.serverId());
        } catch (BusinessException be) {
            quietDisconnect(ssh);
            throw be;
        } catch (IOException e) {
            quietDisconnect(ssh);
            log.warn("SSH 隧道建立失败 server={} target={}:{}: {}",
                    cred.serverId(), remoteHost, remotePort, e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, cred.serverId());
        }
    }

    private static void authenticate(SSHClient ssh, ServerCredentialDTO cred) throws IOException {
        if (StrUtil.isBlank(cred.sshHost()) || StrUtil.isBlank(cred.sshUser())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        if (StrUtil.isBlank(cred.sshPassword()) && StrUtil.isBlank(cred.sshPrivateKey())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        if (StrUtil.isNotBlank(cred.sshPrivateKey())) {
            String keyMaterial = cred.sshPrivateKey();
            KeyProvider kp;
            if (Files.exists(Path.of(keyMaterial))) {
                kp = StrUtil.isNotBlank(cred.sshPrivateKeyPassphrase())
                        ? ssh.loadKeys(keyMaterial, cred.sshPrivateKeyPassphrase())
                        : ssh.loadKeys(keyMaterial);
            } else {
                Path tmp = Files.createTempFile("nook-tunnel-", ".pem");
                tmp.toFile().deleteOnExit();
                Files.writeString(tmp, keyMaterial, StandardCharsets.UTF_8);
                kp = StrUtil.isNotBlank(cred.sshPrivateKeyPassphrase())
                        ? ssh.loadKeys(tmp.toString(), cred.sshPrivateKeyPassphrase())
                        : ssh.loadKeys(tmp.toString());
            }
            ssh.authPublickey(cred.sshUser(), kp);
        } else {
            ssh.authPassword(cred.sshUser(), cred.sshPassword());
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        log.info("关闭 SSH 隧道 server={} local=127.0.0.1:{}", serverId, localPort);
        try { localSocket.close(); } catch (IOException ignored) {}
        try { acceptThread.interrupt(); } catch (Exception ignored) {}
        quietDisconnect(ssh);
    }

    private static void quietDisconnect(SSHClient ssh) {
        try {
            if (ssh.isConnected()) ssh.disconnect();
        } catch (IOException ignored) {
        }
    }
}
