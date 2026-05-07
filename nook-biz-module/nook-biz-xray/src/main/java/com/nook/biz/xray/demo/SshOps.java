package com.nook.biz.xray.demo;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * SSH 远程命令执行。
 * 仅 demo 用途：使用 PromiscuousVerifier 接受任意 host key——**生产里务必换成 known_hosts/指纹校验**。
 */
public class SshOps implements AutoCloseable {

    private final DemoConfig cfg;
    private final SSHClient ssh;

    public SshOps(DemoConfig cfg) throws IOException {
        this.cfg = cfg;
        this.ssh = new SSHClient();
        this.ssh.addHostKeyVerifier(new PromiscuousVerifier());
        this.ssh.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(cfg.timeoutSeconds));
        this.ssh.setTimeout((int) TimeUnit.SECONDS.toMillis(cfg.timeoutSeconds));
        this.ssh.connect(cfg.sshHost, cfg.sshPort);
        if (cfg.sshPrivateKeyPath != null && !cfg.sshPrivateKeyPath.isBlank()) {
            KeyProvider kp = cfg.sshPrivateKeyPassphrase != null && !cfg.sshPrivateKeyPassphrase.isBlank()
                    ? this.ssh.loadKeys(cfg.sshPrivateKeyPath, cfg.sshPrivateKeyPassphrase)
                    : this.ssh.loadKeys(cfg.sshPrivateKeyPath);
            this.ssh.authPublickey(cfg.sshUser, kp);
        } else {
            this.ssh.authPassword(cfg.sshUser, cfg.sshPassword);
        }
    }

    /** 执行命令，返回 (stdout + stderr) 合并文本；非 0 退出码会抛异常。 */
    public String exec(String command) throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command cmd = session.exec(command);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            cmd.getInputStream().transferTo(out);
            // 收一下 stderr，方便排查
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            cmd.getErrorStream().transferTo(err);
            cmd.join(cfg.timeoutSeconds, TimeUnit.SECONDS);
            String stdout = out.toString(StandardCharsets.UTF_8);
            String stderr = err.toString(StandardCharsets.UTF_8);
            Integer exit = cmd.getExitStatus();
            if (exit != null && exit != 0) {
                throw new IOException("命令失败 exit=" + exit + " cmd=" + command + "\nstderr: " + stderr);
            }
            return stdout + (stderr.isEmpty() ? "" : "\n[stderr] " + stderr);
        }
    }

    // ===== 常用 3x-ui 运维操作 =====

    /** x-ui 命令行工具自带的状态查询（service status + 监听端口等）。 */
    public String xuiStatus() throws IOException {
        return exec("x-ui status || systemctl status x-ui --no-pager");
    }

    /** 重启 x-ui 服务。 */
    public String restartXui() throws IOException {
        return exec("systemctl restart x-ui && sleep 1 && systemctl is-active x-ui");
    }

    /** 拉最近 N 行 x-ui 日志。 */
    public String tailXuiLog(int lines) throws IOException {
        return exec("journalctl -u x-ui -n " + lines + " --no-pager");
    }

    /** 显示面板登录信息（端口 / webBasePath / 账号），方便对账 panel.baseUrl。 */
    public String panelInfo() throws IOException {
        return exec("x-ui setting -show true");
    }

    /** 备份 SQLite 数据库到 /tmp（拉回本地另算，可借 sshj.SCPFileTransfer）。 */
    public String backupDb() throws IOException {
        return exec("cp /etc/x-ui/x-ui.db /tmp/x-ui.db.bak && ls -lh /tmp/x-ui.db.bak");
    }

    /** Xray 进程是否在跑。 */
    public String xrayProcess() throws IOException {
        return exec("ps -ef | grep -E 'xray|x-ui' | grep -v grep || true");
    }

    @Override
    public void close() throws IOException {
        if (ssh.isConnected()) {
            ssh.disconnect();
        }
    }
}
