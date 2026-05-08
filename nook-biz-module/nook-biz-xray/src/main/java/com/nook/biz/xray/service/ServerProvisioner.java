package com.nook.biz.xray.service;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
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
     * 一键安装/重装线路服务器(纯 Xray + nook 标配 xray.json)。
     * @return 完整 stdout + stderr (前端透传给用户看)
     */
    public String installLineServer(String serverId, LineServerInstallParams params) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        String script = renderTemplate("scripts/install-line-server.sh.tmpl", buildLineServerVars(params));
        return uploadAndRun(cred, "nook-install-line", script);
    }

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
        return Map.of(
                "SERVER_NAME", StrUtil.blankToDefault(params.serverName, "<unset>"),
                "RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "VMESS_PORT", String.valueOf(params.vmessPort),
                "XRAY_API_PORT", String.valueOf(params.xrayApiPort),
                "LOG_DIR", StrUtil.blankToDefault(params.logDir, "/var/log/xray"),
                "INSTALL_UFW", String.valueOf(params.installUfw),
                "ENABLE_BBR", String.valueOf(params.enableBbr),
                "INSTALL_UFW_BLOCK_LABEL", params.installUfw ? "UFW 防火墙规则" : "(跳过 UFW)",
                "INSTALL_BBR_BLOCK_LABEL", params.enableBbr ? "BBR 内核优化" : "(跳过 BBR)"
        );
    }

    /** 一键安装 SOCKS5 落地节点。 */
    public String installSocks5Landing(String serverId, Socks5LandingInstallParams params) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        String script = renderTemplate("scripts/install-socks5-landing.sh.tmpl", Map.of(
                "RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "SOCKS_PORT", String.valueOf(params.socksPort),
                "SOCKS_USER", params.socksUser,
                "SOCKS_PASS", params.socksPass,
                "ALLOW_FROM", StrUtil.blankToDefault(params.allowFrom, "0.0.0.0/0"),
                "INSTALL_UFW", String.valueOf(params.installUfw)
        ));
        return uploadAndRun(cred, "nook-install-socks5", script);
    }

    /**
     * 取 Xray service 当前状态：active 与否、版本、监听端口、最近 N 行日志。
     * 只发一条复合 shell 命令在一次 SSH 内拿全, 避免多次握手开销.
     */
    public XrayServiceStatus xrayStatus(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        // 用 || true 防止某段失败让整条命令 exit !=0
        String composite = String.join("\n",
                "echo '====[ACTIVE]===='",
                "systemctl is-active xray 2>/dev/null || true",
                "echo '====[VERSION]===='",
                "xray version 2>/dev/null | head -1 || true",
                "echo '====[UPTIME]===='",
                "systemctl show xray -p ActiveEnterTimestamp --value 2>/dev/null || true",
                "echo '====[LISTEN]===='",
                "ss -ltn 2>/dev/null | grep -E '(127.0.0.1:" + (cred.xrayGrpcPort() == null ? 0 : cred.xrayGrpcPort()) + " |:443 |:2087 )' || true",
                "echo '====[LOG]===='",
                "journalctl -u xray -n 30 --no-pager 2>/dev/null || true"
        );
        String out = sshExecutor.exec(cred, composite, 30);
        return XrayServiceStatus.parse(out);
    }

    /** 重启 Xray 服务。 */
    public String restartXray(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        return sshExecutor.exec(cred,
                "systemctl restart xray && sleep 2 && systemctl is-active xray && xray version | head -1",
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
            boolean enableBbr
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
