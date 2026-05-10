package com.nook.biz.node.service.xray.server;

import jakarta.annotation.Resource;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.biz.node.controller.xray.server.vo.ServiceStatusRespVO;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.xray.RemoteFiles;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.framework.xray.server.snapshot.XrayDaemonExtraSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class XrayServerManageServiceImpl implements XrayServerManageService {

    /** 安装超时; apt 拉包慢可能要几分钟, 给 10 分钟兜底. */
    private static final Duration INSTALL_TIMEOUT = Duration.ofSeconds(600);

    /** 单条快速命令超时. */
    private static final Duration QUICK_TIMEOUT = Duration.ofSeconds(30);

    private static final String TMP_PREFIX = "nook-install-xray";

    /** install 模块 classpath 前缀. */
    private static final String MODULE_PATH = "scripts/modules/";

    /** 默认值常量, 与 modules/*.sh.tmpl 的占位对齐. */
    private static final int DEFAULT_SLOT_PORT_BASE = 30000;
    private static final int DEFAULT_SLOT_POOL_SIZE = 50;
    private static final int DEFAULT_XRAY_API_PORT = 8080;
    private static final int DEFAULT_SWAP_SIZE_MB = 1024;

    @Resource
    private SshSessionManager sessionManager;
    @Resource
    private RemoteScriptRunner scriptRunner;
    @Resource
    private ServerProbe serverProbe;
    @Resource
    private XrayDaemonProbe xrayDaemonProbe;

    @Override
    public void installStreaming(String serverId, LineServerInstallReqVO reqVO, Consumer<String> lineSink) {
        Map<String, String> vars = buildInstallVars(serverId, reqVO);
        String script = assembleInstallScript(reqVO, vars);
        scriptRunner.runScriptStreaming(
                sessionManager.acquire(serverId),
                script,
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

    /**
     * 按 reqVO 勾选项把 install 模块拼成完整脚本; 顺序固定 00 → ...→ 99.
     * 必装: 00-prepare-env / 50-xray / 99-finalize.
     * 可选: 10-timezone (timezone != skip) / 20-swap / 30-bbr / 40-ufw.
     */
    private String assembleInstallScript(LineServerInstallReqVO r, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder(8192);
        // 公共 header: bash + set -euo pipefail (各模块不再重复写)
        sb.append("#!/usr/bin/env bash\n");
        sb.append("# nook server 模块化部署脚本, 渲染于 ").append(vars.get("RENDER_AT")).append("\n");
        sb.append("# server: ").append(vars.get("SERVER_NAME")).append("\n");
        sb.append("set -euo pipefail\n\n");

        appendModule(sb, "00-prepare-env.sh.tmpl", vars);

        if (StrUtil.isNotBlank(r.getTimezone()) && !"skip".equalsIgnoreCase(r.getTimezone())) {
            appendModule(sb, "10-timezone.sh.tmpl", vars);
        }
        if (Boolean.TRUE.equals(r.getInstallSwap())) {
            appendModule(sb, "20-swap.sh.tmpl", vars);
        }
        if (Boolean.TRUE.equals(r.getEnableBbr())) {
            appendModule(sb, "30-bbr.sh.tmpl", vars);
        }
        if (Boolean.TRUE.equals(r.getInstallUfw())) {
            appendModule(sb, "40-ufw.sh.tmpl", vars);
        }

        appendModule(sb, "50-xray.sh.tmpl", vars);
        appendModule(sb, "99-finalize.sh.tmpl", vars);

        return sb.toString();
    }

    private void appendModule(StringBuilder sb, String moduleFile, Map<String, String> vars) {
        sb.append(scriptRunner.renderTemplate(MODULE_PATH + moduleFile, vars)).append("\n");
    }

    /**
     * 部署模板渲染变量表; 各模块共用一套占位.
     * 默认值与 LineServerInstallReqVO 字段语义对齐 (null 时回落默认).
     */
    private Map<String, String> buildInstallVars(String serverId, LineServerInstallReqVO r) {
        int slotPortBase = r.getSlotPortBase() != null ? r.getSlotPortBase() : DEFAULT_SLOT_PORT_BASE;
        int slotPoolSize = r.getSlotPoolSize() != null ? r.getSlotPoolSize() : DEFAULT_SLOT_POOL_SIZE;
        int slotPortEnd = slotPortBase + slotPoolSize;
        int xrayApiPort = r.getXrayApiPort() != null ? r.getXrayApiPort() : DEFAULT_XRAY_API_PORT;
        int swapSizeMb = r.getSwapSizeMb() != null ? r.getSwapSizeMb() : DEFAULT_SWAP_SIZE_MB;
        String xrayVersion = StrUtil.blankToDefault(r.getXrayVersion(), RemoteFiles.XRAY_DEFAULT_VERSION);
        String timezone = StrUtil.blankToDefault(r.getTimezone(), "skip");
        boolean installUfw = Boolean.TRUE.equals(r.getInstallUfw());
        boolean enableBbr = Boolean.TRUE.equals(r.getEnableBbr());
        boolean installSwap = Boolean.TRUE.equals(r.getInstallSwap());

        // 用 LinkedHashMap 保留插入顺序便于 debug
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", StrUtil.blankToDefault(serverId, "<unset>"));
        vars.put("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        vars.put("TIMEZONE", timezone);
        vars.put("INSTALL_UFW", String.valueOf(installUfw));
        vars.put("ENABLE_BBR", String.valueOf(enableBbr));
        vars.put("INSTALL_SWAP", String.valueOf(installSwap));
        vars.put("SWAP_SIZE_MB", String.valueOf(swapSizeMb));
        vars.put("XRAY_VERSION", xrayVersion);
        vars.put("XRAY_API_PORT", String.valueOf(xrayApiPort));
        vars.put("LOG_DIR", StrUtil.blankToDefault(r.getLogDir(), RemoteFiles.LOG_DIR));
        vars.put("SLOT_PORT_BASE", String.valueOf(slotPortBase));
        vars.put("SLOT_PORT_END", String.valueOf(slotPortEnd));
        vars.put("SLOT_POOL_SIZE", String.valueOf(slotPoolSize));
        return vars;
    }
}
