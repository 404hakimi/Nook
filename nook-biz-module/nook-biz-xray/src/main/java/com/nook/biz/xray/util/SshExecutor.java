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
 * 生产应该改为 known_hosts/指纹白名单——TODO，等批量上量后做。
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
     * <p>命令最大耗时取自 {@link ServerCredentialDTO#sshTimeoutSeconds()}(由管理端配置)，
     * 缺失时走 {@link #FALLBACK_OP_TIMEOUT_SECONDS}。
     */
    public String exec(ServerCredentialDTO cred, String command) {
        if (StrUtil.isBlank(cred.sshHost()) || StrUtil.isBlank(cred.sshUser())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        if (StrUtil.isBlank(cred.sshPassword()) && StrUtil.isBlank(cred.sshPrivateKey())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        int opTimeout = (cred.sshTimeoutSeconds() == null || cred.sshTimeoutSeconds() <= 0)
                ? FALLBACK_OP_TIMEOUT_SECONDS
                : cred.sshTimeoutSeconds();
        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            // connect 用短超时(只 TCP 握手 + SSH 协商)；read 用 opTimeout 覆盖整条命令的 idle 等待
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
                // 视为内联 PEM：sshj 没有 loadKeys(String pem) 重载，写到临时文件
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
