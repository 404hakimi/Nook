package com.nook.biz.node.service.xray.server;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.biz.node.controller.xray.server.vo.ServiceStatusRespVO;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.server.session.ServerSessionManager;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.xray.RemoteFiles;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.framework.xray.server.snapshot.XrayDaemonExtraSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class XrayServerManageServiceImpl implements XrayServerManageService {

    /** 安装超时; apt 拉包慢可能要几分钟, 给 10 分钟兜底. */
    private static final Duration INSTALL_TIMEOUT = Duration.ofSeconds(600);

    /** 单条快速命令超时. */
    private static final Duration QUICK_TIMEOUT = Duration.ofSeconds(30);

    private static final String TMPL_INSTALL = "scripts/install-line-server.sh.tmpl";
    private static final String TMP_PREFIX = "nook-install-xray";

    private final ServerSessionManager sessionManager;
    private final RemoteScriptRunner scriptRunner;
    private final ServerProbe serverProbe;
    private final XrayDaemonProbe xrayDaemonProbe;

    @Override
    public void installStreaming(String serverId, LineServerInstallReqVO reqVO, Consumer<String> lineSink) {
        scriptRunner.runFromTemplateStreaming(
                sessionManager.acquire(serverId),
                TMPL_INSTALL,
                buildInstallVars(serverId, reqVO),
                TMP_PREFIX,
                INSTALL_TIMEOUT,
                lineSink);
    }

    @Override
    public String restart(String serverId) {
        String unit = RemoteFiles.SYSTEMD_UNIT;
        // restart → sleep 2 → is-active → xray version: 一条复合命令拿"是否活+版本"做前端展示
        return sessionManager.acquire(serverId).ssh().exec(
                "systemctl restart " + unit + " && sleep 2 && systemctl is-active " + unit
                        + " && xray version | head -1",
                QUICK_TIMEOUT
        ).stdout();
    }

    @Override
    public ServiceStatusRespVO status(String serverId) {
        // ServerProbe 只回通用 systemd 状态 (active/uptime/enabled); xray 专属 (version + 监听端口) 走 XrayDaemonProbe
        SystemdStatusSnapshot sysd = serverProbe.readSystemdStatus(serverId, RemoteFiles.SYSTEMD_UNIT);
        XrayDaemonExtraSnapshot extras = xrayDaemonProbe.readExtras(serverId);

        ServiceStatusRespVO vo = new ServiceStatusRespVO();
        vo.setUnit(sysd.unit());
        vo.setActive(sysd.active());
        vo.setUptimeFrom(sysd.uptimeFrom());
        vo.setEnabled(sysd.enabled());
        vo.setVersion(extras.version());
        vo.setListening(extras.listening());
        return vo;
    }

    @Override
    public String setAutostart(String serverId, boolean enabled) {
        String unit = RemoteFiles.SYSTEMD_UNIT;
        // enable/disable 退出码非 0 也算正常 (already-enabled 等), 用 || true 兜底; 末尾 is-enabled 给前端确认结果
        String op = enabled ? "enable" : "disable";
        return sessionManager.acquire(serverId).ssh().exec(
                "systemctl " + op + " " + unit + " 2>&1 || true; systemctl is-enabled " + unit + " 2>/dev/null || true",
                QUICK_TIMEOUT
        ).stdout();
    }

    private Map<String, String> buildInstallVars(String serverId, LineServerInstallReqVO r) {
        boolean installUfw = r.getInstallUfw() != null && r.getInstallUfw();
        boolean enableBbr = r.getEnableBbr() != null && r.getEnableBbr();
        return Map.ofEntries(
                Map.entry("SERVER_NAME", StrUtil.blankToDefault(serverId, "<unset>")),
                Map.entry("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.entry("VMESS_PORT", String.valueOf(r.getVmessPort())),
                Map.entry("XRAY_API_PORT", String.valueOf(r.getXrayApiPort())),
                Map.entry("LOG_DIR", StrUtil.blankToDefault(r.getLogDir(), RemoteFiles.LOG_DIR)),
                Map.entry("INSTALL_UFW", String.valueOf(installUfw)),
                Map.entry("ENABLE_BBR", String.valueOf(enableBbr)),
                Map.entry("TIMEZONE", StrUtil.blankToDefault(r.getTimezone(), "skip")),
                Map.entry("INSTALL_UFW_BLOCK_LABEL", installUfw ? "UFW 防火墙规则" : "(跳过 UFW)"),
                Map.entry("INSTALL_BBR_BLOCK_LABEL", enableBbr ? "BBR 内核优化" : "(跳过 BBR)"));
    }
}
