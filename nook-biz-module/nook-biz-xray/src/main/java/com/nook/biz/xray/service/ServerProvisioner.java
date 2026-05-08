package com.nook.biz.xray.service;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.constant.XrayConstants;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.biz.xray.util.SshExecutor;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 服务器一键运维：渲染脚本模板 → SCP 到远端 → 执行 → 收集输出。
 *
 * <p>设计要点:
 *   - 脚本模板放 classpath:scripts/*.tmpl, 占位符 {{VAR}};
 *   - 渲染后 base64 上传到 /tmp/nook-install-<ts>.sh, 避免 shell 转义;
 *   - 执行用 600s 超时(默认 install 不超过 5 分钟);
 *   - 全程在一条 SSH 上跑;失败不重试,前端展示错误日志后由用户决定.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerProvisioner {

    /** 安装类命令默认超时(秒)；apt 拉包慢可能要几分钟 */
    public static final int INSTALL_TIMEOUT_SECONDS = 600;

    private final ResourceServerApi resourceServerApi;
    private final SshExecutor sshExecutor;

    /**
     * 一键安装/重装的流式版本: 远端 stdout 每来一行就回调 lineConsumer.
     * 上传脚本与 nook 自身的进度提示也会走 lineConsumer, 让前端连贯展示.
     * 抛 BusinessException 时上层应把异常 message 也送给前端.
     */
    public void installLineServerStreaming(String serverId,
                                           LineServerInstallParams params,
                                           Consumer<String> lineConsumer) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        String script = renderTemplate("scripts/install-line-server.sh.tmpl", buildLineServerVars(params));
        long ts = System.currentTimeMillis();
        String remote = "/tmp/nook-install-line-" + ts + ".sh";

        lineConsumer.accept("[nook] 渲染模板完成, 大小 " + script.length() + " bytes");
        lineConsumer.accept("[nook] 上传到远端 " + remote + " ...");
        sshExecutor.uploadString(cred, remote, script, 30);
        lineConsumer.accept("[nook] 上传完成, 开始执行(超时 " + INSTALL_TIMEOUT_SECONDS + "s)");
        lineConsumer.accept("[nook] ────────────────────────────────────────");

        String cmd = "bash '" + remote + "' 2>&1; rc=$?; rm -f '" + remote + "'; exit $rc";
        sshExecutor.execStreaming(cred, cmd, INSTALL_TIMEOUT_SECONDS, lineConsumer);

        lineConsumer.accept("[nook] ────────────────────────────────────────");
        lineConsumer.accept("[nook] 远端脚本已结束, 临时文件已清理");
    }

    private Map<String, String> buildLineServerVars(LineServerInstallParams params) {
        return Map.ofEntries(
                Map.entry("SERVER_NAME", StrUtil.blankToDefault(params.serverName, "<unset>")),
                Map.entry("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.entry("VMESS_PORT", String.valueOf(params.vmessPort)),
                Map.entry("XRAY_API_PORT", String.valueOf(params.xrayApiPort)),
                Map.entry("LOG_DIR", StrUtil.blankToDefault(params.logDir, XrayConstants.REMOTE_LOG_DIR)),
                Map.entry("INSTALL_UFW", String.valueOf(params.installUfw)),
                Map.entry("ENABLE_BBR", String.valueOf(params.enableBbr)),
                Map.entry("TIMEZONE", StrUtil.blankToDefault(params.timezone, "skip")),
                Map.entry("INSTALL_UFW_BLOCK_LABEL", params.installUfw ? "UFW 防火墙规则" : "(跳过 UFW)"),
                Map.entry("INSTALL_BBR_BLOCK_LABEL", params.enableBbr ? "BBR 内核优化" : "(跳过 BBR)")
        );
    }

    /** 一键安装 SOCKS5 落地节点(查表凭据)；用于已注册 resource_server 的主机。 */
    public String installSocks5Landing(String serverId, Socks5LandingInstallParams params) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        return installSocks5LandingWithCred(cred, params);
    }

    /**
     * 一键安装 SOCKS5(凭据外部传入)；IP 池的落地节点不在 resource_server 表中, 调用方自带 SSH 凭据。
     */
    public String installSocks5LandingWithCred(ServerCredentialDTO cred, Socks5LandingInstallParams params) {
        String script = renderTemplate("scripts/install-socks5-landing.sh.tmpl", buildSocks5Vars(params));
        return uploadAndRun(cred, "nook-install-socks5", script);
    }

    /**
     * 流式版本: 凭据外部传入, 远端 stdout 逐行回调; 用于 IP 池一键部署 SOCKS5.
     * 抛 BusinessException 时上层应把异常 message 也送给前端.
     */
    public void installSocks5LandingStreaming(ServerCredentialDTO cred,
                                              Socks5LandingInstallParams params,
                                              Consumer<String> lineConsumer) {
        String script = renderTemplate("scripts/install-socks5-landing.sh.tmpl", buildSocks5Vars(params));
        long ts = System.currentTimeMillis();
        String remote = "/tmp/nook-install-socks5-" + ts + ".sh";

        lineConsumer.accept("[nook] 渲染模板完成, 大小 " + script.length() + " bytes");
        lineConsumer.accept("[nook] 上传到远端 " + remote + " ...");
        sshExecutor.uploadString(cred, remote, script, 30);
        lineConsumer.accept("[nook] 上传完成, 开始执行(超时 " + INSTALL_TIMEOUT_SECONDS + "s)");
        lineConsumer.accept("[nook] ────────────────────────────────────────");

        String cmd = "bash '" + remote + "' 2>&1; rc=$?; rm -f '" + remote + "'; exit $rc";
        sshExecutor.execStreaming(cred, cmd, INSTALL_TIMEOUT_SECONDS, lineConsumer);

        lineConsumer.accept("[nook] ────────────────────────────────────────");
        lineConsumer.accept("[nook] 远端脚本已结束, 临时文件已清理");
    }

    private Map<String, String> buildSocks5Vars(Socks5LandingInstallParams params) {
        return Map.of(
                "RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "SOCKS_PORT", String.valueOf(params.socksPort),
                "SOCKS_USER", params.socksUser,
                "SOCKS_PASS", params.socksPass,
                "ALLOW_FROM", StrUtil.blankToDefault(params.allowFrom, "0.0.0.0/0"),
                "INSTALL_UFW", String.valueOf(params.installUfw)
        );
    }

    /**
     * 取 Xray service 当前状态 + 系统基本信息 + 最近 N 行日志(可按 systemd 优先级过滤).
     * 一次 SSH 拿全, 避免多次握手.
     *
     * @param logLines  日志行数; null/<=0 走默认 30
     * @param logLevel  日志级别过滤: null/"all" 不过滤; "warning"/"err" 走 journalctl -p
     */
    public XrayServiceStatus xrayStatus(String serverId, Integer logLines, String logLevel) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        int lines = (logLines == null || logLines <= 0) ? 30 : Math.min(logLines, 5000);
        String priorityFlag = "";
        if (StrUtil.isNotBlank(logLevel)) {
            String lvl = logLevel.trim().toLowerCase();
            // journalctl -p 接受名字或数字; 我们只暴露三档
            switch (lvl) {
                case "warning", "warn" -> priorityFlag = "-p warning";
                case "err", "error" -> priorityFlag = "-p err";
                default -> priorityFlag = ""; // all
            }
        }
        String unit = XrayConstants.SYSTEMD_UNIT;
        String composite = String.join("\n",
                "echo '====[ACTIVE]===='",
                "systemctl is-active " + unit + " 2>/dev/null || true",
                "echo '====[VERSION]===='",
                "xray version 2>/dev/null | head -1 || true",
                "echo '====[UPTIME]===='",
                "systemctl show " + unit + " -p ActiveEnterTimestamp --value 2>/dev/null || true",
                "echo '====[LISTEN]===='",
                "ss -ltn 2>/dev/null | grep -E '(127.0.0.1:" + cred.xrayGrpcPort() + " |:443 |:2087 )' || true",
                "echo '====[HOSTNAME]===='",
                "hostname 2>/dev/null || true",
                "echo '====[KERNEL]===='",
                "uname -srm 2>/dev/null || true",
                "echo '====[OS_RELEASE]===='",
                ". /etc/os-release 2>/dev/null && echo \"$PRETTY_NAME\" || true",
                "echo '====[SYS_UPTIME]===='",
                "uptime -p 2>/dev/null || true",
                "echo '====[LOADAVG]===='",
                "cut -d' ' -f1-3 /proc/loadavg 2>/dev/null || true",
                "echo '====[MEMORY]===='",
                // 输出: '2.1G / 7.7G (28%)'
                "free -m 2>/dev/null | awk '/^Mem:/ {used=$3; total=$2; printf \"%.1fG / %.1fG (%.0f%%)\\n\", used/1024, total/1024, used*100/total}' || true",
                "echo '====[DISK]===='",
                "df -h / 2>/dev/null | awk 'NR==2 {printf \"%s / %s (%s)\\n\", $3, $2, $5}' || true",
                "echo '====[TIMEZONE]===='",
                "timedatectl show -p Timezone --value 2>/dev/null || date +%Z || true",
                "echo '====[LOG]===='",
                "journalctl -u " + unit + " " + priorityFlag + " -n " + lines + " --no-pager 2>/dev/null || true"
        );
        String out = sshExecutor.exec(cred, composite, 30);
        return XrayServiceStatus.parse(out);
    }

    /** 重启 Xray 服务。 */
    public String restartXray(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        String unit = XrayConstants.SYSTEMD_UNIT;
        return sshExecutor.exec(cred,
                "systemctl restart " + unit + " && sleep 2 && systemctl is-active " + unit
                        + " && xray version | head -1",
                30);
    }

    // ===== helpers =====

    private String renderTemplate(String classpath, Map<String, String> vars) {
        String tmpl;
        try {
            tmpl = ResourceUtil.readUtf8Str(classpath);
        } catch (Exception e) {
            log.error("读取脚本模板失败: {}", classpath, e);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    "<template>", "无法读取模板 " + classpath);
        }
        for (Map.Entry<String, String> v : vars.entrySet()) {
            tmpl = tmpl.replace("{{" + v.getKey() + "}}", v.getValue());
        }
        return tmpl;
    }

    /** 把脚本传到 /tmp/<prefix>-<ts>.sh 并执行;长超时,完整输出回传. */
    private String uploadAndRun(ServerCredentialDTO cred, String prefix, String script) {
        long ts = System.currentTimeMillis();
        String remote = "/tmp/" + prefix + "-" + ts + ".sh";
        log.info("[provisioner] upload {} bytes -> {} server={}",
                script.length(), remote, cred.serverId());
        sshExecutor.uploadString(cred, remote, script, 30);
        log.info("[provisioner] running {} server={}", remote, cred.serverId());
        try {
            String out = sshExecutor.exec(cred,
                    "bash '" + remote + "' 2>&1; rc=$?; rm -f '" + remote + "'; exit $rc",
                    INSTALL_TIMEOUT_SECONDS);
            log.info("[provisioner] OK server={} outputBytes={}", cred.serverId(), out.length());
            return out;
        } catch (BusinessException be) {
            log.warn("[provisioner] FAIL server={} code={} msg={}",
                    cred.serverId(), be.getCode(), be.getMessage());
            throw be;
        }
    }

    // ===== params records =====

    /** 线路服务器安装参数 */
    public record LineServerInstallParams(
            String serverName,
            int vmessPort,
            int xrayApiPort,
            String logDir,
            boolean installUfw,
            boolean enableBbr,
            String timezone
    ) {
    }

    /** SOCKS5 落地节点安装参数 */
    public record Socks5LandingInstallParams(
            int socksPort,
            String socksUser,
            String socksPass,
            String allowFrom,
            boolean installUfw
    ) {
    }
}
