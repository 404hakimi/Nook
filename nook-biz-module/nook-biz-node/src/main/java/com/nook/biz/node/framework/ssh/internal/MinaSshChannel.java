package com.nook.biz.node.framework.ssh.internal;

import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.SshChannel;
import com.nook.biz.node.framework.ssh.dto.SshExecResult;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * SshChannel 的 MINA SSHD 实现, 复用宿主 session 的 ClientSession 跑 exec channel.
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
public class MinaSshChannel implements SshChannel {

    private final String serverId;
    private final ClientSession clientSession;

    @Override
    public SshExecResult exec(String cmd, Duration timeout) {
        long startNs = System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (ChannelExec ch = clientSession.createExecChannel(cmd)) {
            ch.setOut(out);
            ch.setErr(err);
            ch.open().verify(timeout.toMillis());
            ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout.toMillis());
            Integer exit = ch.getExitStatus();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNs);
            String stdout = out.toString(StandardCharsets.UTF_8);
            String stderr = err.toString(StandardCharsets.UTF_8);
            if (exit == null || exit != 0) {
                throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                        serverId, "exit=" + exit + " stderr=" + stderr);
            }
            return new SshExecResult(exit, stdout, stderr, elapsed);
        } catch (BusinessException be) {
            throw be;
        } catch (IOException e) {
            log.warn("[ssh] exec 失败 server={} cmd={}: {}", serverId, cmd, e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, e, serverId);
        }
    }

    @Override
    public SshExecResult execStream(String cmd, Duration timeout, Consumer<String> lineConsumer) {
        long startNs = System.nanoTime();
        // PipedOutput 容量 64KB, 远端单行 stdout 不应超过此值; 超过会阻塞 MINA 写线程
        PipedOutputStream stdoutPipe;
        PipedInputStream stdoutRead;
        try {
            stdoutPipe = new PipedOutputStream();
            stdoutRead = new PipedInputStream(stdoutPipe, 64 * 1024);
        } catch (IOException e) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, e,
                    serverId, "建 piped stream 失败");
        }
        ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();

        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stdoutRead, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            } catch (IOException ignored) {
                // pipe 关闭即正常 EOF
            }
        }, "ssh-stream-" + serverId);
        reader.setDaemon(true);
        reader.start();

        try (ChannelExec ch = clientSession.createExecChannel(cmd)) {
            ch.setOut(stdoutPipe);
            ch.setErr(stderrBuf);
            ch.open().verify(timeout.toMillis());
            ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout.toMillis());
            Integer exit = ch.getExitStatus();
            // 关 pipe 让 reader 自然 EOF 退出
            try { stdoutPipe.close(); } catch (IOException ignored) { }
            try { reader.join(2_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNs);
            if (exit == null || exit != 0) {
                throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                        serverId, "exit=" + exit + " stderr=" + stderrBuf.toString(StandardCharsets.UTF_8));
            }
            // 流式场景 stdout 已被 lineConsumer 实时消费, 这里返回空串避免重复占内存
            return new SshExecResult(exit, "", stderrBuf.toString(StandardCharsets.UTF_8), elapsed);
        } catch (BusinessException be) {
            throw be;
        } catch (IOException e) {
            log.warn("[ssh] execStream 失败 server={} cmd={}: {}", serverId, cmd, e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, e, serverId);
        }
    }

    @Override
    public void uploadString(String remotePath, String content, Duration timeout) {
        // 用 base64+exec 而非 SFTP, 不引入 sshd-sftp 子模块依赖; 大文件场景再切 SFTP
        String b64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String safePath = remotePath.replace("'", "'\\''");
        String cmd = "echo '" + b64 + "' | base64 -d > '" + safePath + "' && chmod 600 '" + safePath + "'";
        exec(cmd, timeout);
    }
}
