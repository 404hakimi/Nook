package com.nook.biz.xray.util;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 远程 SSH 命令执行；常用于 x-ui 运维(查状态/重启/拉日志/SQLite 备份)。
 *
 * <p>当前实现是无状态的——每次 exec 新建一个连接。多机/高频场景再加连接池(sshj 自身没池子，
 * 需要自己用 GenericObjectPool 之类包，先不做)。
 *
 * <p><b>安全：</b>使用 {@link PromiscuousVerifier}(接受任何 host key)，**仅适合受控环境**。
 * 生产应改为 known_hosts/指纹白名单。
 * TODO(@team, 2026-06-30): 接 known_hosts 校验，给 resource_server 加 ssh_host_fingerprint 字段。
 */
@Slf4j
@Component
public class SshExecutor {

    /** TCP 建连超时；SSH 握手不应该慢于这个 */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    /** 当 cred.sshTimeoutSeconds 为空(老数据 / 未走表的 ad-hoc 调用)的兜底值 */
    public static final int FALLBACK_OP_TIMEOUT_SECONDS = 30;

    /**
     * 在远端执行单条命令；命令非 0 退出码会抛 BusinessException(BACKEND_OPERATION_FAILED)。
     * 返回的字符串 = stdout + (有 stderr 时 + "\n[stderr] " + stderr)。
     *
     * <p>超时取自 {@link ServerCredentialDTO#sshTimeoutSeconds()}(由管理端配置)，缺失走 fallback。
     * <p>慢命令(安装脚本/拉大日志)请用 {@link #exec(ServerCredentialDTO, String, int)} 显式给更长超时。
     */
    public String exec(ServerCredentialDTO cred, String command) {
        Integer t = cred.sshTimeoutSeconds();
        return exec(cred, command, (t == null || t <= 0) ? FALLBACK_OP_TIMEOUT_SECONDS : t);
    }

    /**
     * 显式 op 超时的 exec 重载——一键安装、拉日志这种慢命令用。
     * @param opTimeoutSeconds 单次命令最大耗时(socket read + cmd.join)；建议 60-1200
     */
    public String exec(ServerCredentialDTO cred, String command, int opTimeoutSeconds) {
        if (StrUtil.isBlank(cred.sshHost()) || StrUtil.isBlank(cred.sshUser())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        if (StrUtil.isBlank(cred.sshPassword()) && StrUtil.isBlank(cred.sshPrivateKey())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        int opTimeout = opTimeoutSeconds <= 0 ? FALLBACK_OP_TIMEOUT_SECONDS : opTimeoutSeconds;
        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SECONDS));
            ssh.setTimeout((int) TimeUnit.SECONDS.toMillis(opTimeout));
            ssh.connect(cred.sshHost(), cred.sshPort());
            authenticate(ssh, cred);
            return runCommand(ssh, command, cred, opTimeout);
        } catch (BusinessException be) {
            throw be;
        } catch (IOException e) {
            log.warn("SSH 失败 server={} cmd={}: {}", cred.serverId(), command, e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, cred.serverId());
        }
    }

    /**
     * 把字符串内容写到远端文件——用于把渲染好的安装脚本上传到 /tmp/xxx.sh 再执行。
     * 走 SFTP，比 cat-here-doc 安全(不会被 shell 元字符破坏)。
     */
    public void uploadString(ServerCredentialDTO cred, String remotePath, String content, int opTimeoutSeconds) {
        if (StrUtil.isBlank(cred.sshHost()) || StrUtil.isBlank(cred.sshUser())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        int opTimeout = opTimeoutSeconds <= 0 ? FALLBACK_OP_TIMEOUT_SECONDS : opTimeoutSeconds;
        // 走 cat > path 的方式上传; sshj 也有 SFTP, 但 cat 更轻量(不需要 SFTP 子系统)
        // base64 编码避免 shell 转义问题
        String b64 = java.util.Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        // 注意 'path' 用单引号包, 防特殊字符;
        String safePath = remotePath.replace("'", "'\\''");
        String cmd = "echo '" + b64 + "' | base64 -d > '" + safePath + "' && chmod 600 '" + safePath + "'";
        exec(cred, cmd, opTimeout);
    }

    private static void authenticate(SSHClient ssh, ServerCredentialDTO cred) throws IOException {
        if (StrUtil.isNotBlank(cred.sshPrivateKey())) {
            // 私钥可能是文件路径或 PEM 文本：路径就 loadKeys(path)，否则写临时文件再 load
            String keyMaterial = cred.sshPrivateKey();
            KeyProvider kp;
            if (Files.exists(Path.of(keyMaterial))) {
                kp = StrUtil.isNotBlank(cred.sshPrivateKeyPassphrase())
                        ? ssh.loadKeys(keyMaterial, cred.sshPrivateKeyPassphrase())
                        : ssh.loadKeys(keyMaterial);
            } else {
                // 视为内联 PEM：sshj 没有 loadKeys(String pem) 重载，写到临时文件再 load。
                // TODO(@team, 2026-06-30): JVM kill 时 deleteOnExit 不生效；
                // 应在 try-finally 里 Files.deleteIfExists 显式删，并设 600 权限(setPosixFilePermissions)
                Path tmp = Files.createTempFile("nook-ssh-", ".pem");
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

    private static String runCommand(SSHClient ssh, String command, ServerCredentialDTO cred, int timeoutSeconds)
            throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command cmd = session.exec(command);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            cmd.getInputStream().transferTo(out);
            cmd.getErrorStream().transferTo(err);
            cmd.join(timeoutSeconds, TimeUnit.SECONDS);
            String stdout = out.toString(StandardCharsets.UTF_8);
            String stderr = err.toString(StandardCharsets.UTF_8);
            Integer exit = cmd.getExitStatus();
            if (exit != null && exit != 0) {
                throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                        cred.serverId(), "exit=" + exit + " stderr=" + stderr);
            }
            return stdout + (stderr.isEmpty() ? "" : "\n[stderr] " + stderr);
        }
    }

    // ===== x-ui 常用命令封装 =====

    /** x-ui 服务状态。3x-ui 优先 `x-ui status`；老脚本 fallback 到 systemctl。 */
    public String xuiStatus(ServerCredentialDTO cred) {
        return exec(cred, "x-ui status || systemctl status x-ui --no-pager");
    }

    /** 重启 x-ui。 */
    public String restartXui(ServerCredentialDTO cred) {
        return exec(cred, "systemctl restart x-ui && sleep 1 && systemctl is-active x-ui");
    }

    /** 拉最近 N 行 x-ui 日志；行数大时 journalctl 慢，调用方根据网络在 server 设置里调超时。 */
    public String tailXuiLog(ServerCredentialDTO cred, int lines) {
        return exec(cred, "journalctl -u x-ui -n " + lines + " --no-pager");
    }

    /** 备份 SQLite DB 到 /tmp/x-ui.db.bak；DB 大时 cp 慢，调高 server.sshTimeoutSeconds。 */
    public String backupXuiDb(ServerCredentialDTO cred) {
        return exec(cred, "cp /etc/x-ui/x-ui.db /tmp/x-ui.db.bak && ls -lh /tmp/x-ui.db.bak");
    }

    /** 看 xray 进程是否在跑(诊断用)。 */
    public String xrayProcess(ServerCredentialDTO cred) {
        return exec(cred, "ps -ef | grep -E 'xray|x-ui' | grep -v grep || true");
    }
}
