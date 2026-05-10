package com.nook.biz.node.service.xray.server;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.biz.node.controller.xray.server.vo.ServiceStatusRespVO;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.server.script.config.RemoteScriptPaths;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.framework.xray.server.snapshot.XrayDaemonExtraSnapshot;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import jakarta.annotation.Resource;
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

    @Resource
    private SshSessionManager sessionManager;
    @Resource
    private RemoteScriptRunner scriptRunner;
    @Resource
    private ServerProbe serverProbe;
    @Resource
    private XrayDaemonProbe xrayDaemonProbe;
    @Resource
    private XrayNodeService xrayNodeService;

    @Override
    public void installStreaming(String serverId, LineServerInstallReqVO reqVO, Consumer<String> lineSink) {
        SshSession session = sessionManager.acquire(serverId);
        Map<String, String> vars = buildInstallVars(serverId, reqVO);
        String script = assembleInstallScript(reqVO, vars);
        Duration installTimeout = Duration.ofSeconds(session.cred().getInstallTimeoutSeconds());
        scriptRunner.runScriptStreaming(
                session,
                script,
                RemoteScriptPaths.INSTALL_XRAY_TMP,
                installTimeout,
                lineSink);

        // 部署完成 → 初始化 nook 内部状态. xrayNodeService.upsert 内部同事务初始化 slot 池, 杜绝半初始化态.
        // 失败时事务回滚 + 抛错让 emitter 红色完成, 远端 xray 进程已就绪 (可独立重跑 install 修复 nook 状态).
        try {
            xrayNodeService.upsert(
                    serverId,
                    reqVO.getXrayVersion(),
                    reqVO.getXrayApiPort(),
                    reqVO.getLogDir(),
                    reqVO.getSlotPoolSize(),
                    reqVO.getSlotPortBase());
            lineSink.accept("[nook] ✔ xray_node + slot 池已初始化 (size=" + reqVO.getSlotPoolSize()
                    + ", portBase=" + reqVO.getSlotPortBase() + ")\n");
        } catch (RuntimeException e) {
            log.error("[install] xray 部署成功但 nook 状态初始化失败 server={}, 已自动回滚 DB", serverId, e);
            lineSink.accept("[nook] ⚠ 远端部署 OK, 但 nook 状态初始化失败 (DB 已回滚): "
                    + e.getMessage() + " (重新点部署即可幂等修复)\n");
            throw e;
        }
    }

    @Override
    public String restart(String serverId) {
        SshSession session = sessionManager.acquire(serverId);
        String unit = XrayConstants.SYSTEMD_UNIT;
        // restart → sleep 2 → is-active → xray version: 一条复合命令拿"是否活+版本"做前端展示
        return session.ssh().exec(
                "systemctl restart " + unit + " && sleep 2 && systemctl is-active " + unit
                        + " && xray version | head -1"
        ).getStdout();
    }

    @Override
    public ServiceStatusRespVO status(String serverId) {
        // ServerProbe 只回通用 systemd 状态 (active/uptime/enabled); xray 专属 (version + 监听端口) 走 XrayDaemonProbe
        SystemdStatusSnapshot sysd = serverProbe.readSystemdStatus(serverId, XrayConstants.SYSTEMD_UNIT);
        int apiPort = xrayNodeService.loadOrThrow(serverId).getXrayApiPort();
        XrayDaemonExtraSnapshot extras = xrayDaemonProbe.readExtras(serverId, apiPort);

        ServiceStatusRespVO vo = new ServiceStatusRespVO();
        vo.setUnit(sysd.getUnit());
        vo.setActive(sysd.getActive());
        vo.setUptimeFrom(sysd.getUptimeFrom());
        vo.setEnabled(sysd.getEnabled());
        vo.setVersion(extras.getVersion());
        vo.setListening(extras.getListening());
        return vo;
    }

    @Override
    public String setAutostart(String serverId, boolean enabled) {
        SshSession session = sessionManager.acquire(serverId);
        String unit = XrayConstants.SYSTEMD_UNIT;
        // enable/disable 退出码非 0 也算正常 (already-enabled 等), 用 || true 兜底; 末尾 is-enabled 给前端确认结果
        String op = enabled ? "enable" : "disable";
        return session.ssh().exec(
                "systemctl " + op + " " + unit + " 2>&1 || true; systemctl is-enabled " + unit + " 2>/dev/null || true"
        ).getStdout();
    }

    /** 按 reqVO 勾选项把 install 模块拼成完整脚本; 必装 00/50/99, 可选 10-timezone/40-ufw; swap/bbr 不在此链路. */
    private String assembleInstallScript(LineServerInstallReqVO r, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder(8192);
        // 公共 header: bash + set -euo pipefail (各模块不再重复写)
        sb.append("#!/usr/bin/env bash\n");
        sb.append("# nook server 模块化部署脚本, 渲染于 ").append(vars.get("RENDER_AT")).append("\n");
        sb.append("# server: ").append(vars.get("SERVER_NAME")).append("\n");
        sb.append("set -euo pipefail\n\n");

        appendModule(sb, "00-prepare-env.sh.tmpl", vars);

        if (!"skip".equalsIgnoreCase(r.getTimezone())) {
            appendModule(sb, "10-timezone.sh.tmpl", vars);
        }
        if (Boolean.TRUE.equals(r.getInstallUfw())) {
            appendModule(sb, "40-ufw.sh.tmpl", vars);
        }

        appendModule(sb, "50-xray.sh.tmpl", vars);
        appendModule(sb, "99-finalize.sh.tmpl", vars);

        return sb.toString();
    }

    private void appendModule(StringBuilder sb, String moduleFile, Map<String, String> vars) {
        sb.append(scriptRunner.renderTemplate(RemoteScriptPaths.INSTALL_MODULES_DIR + moduleFile, vars)).append("\n");
    }

    /** 部署模板渲染变量表; reqVO 字段已被 jakarta @Valid + @AssertTrue 校验, 这里直接拆箱. */
    private Map<String, String> buildInstallVars(String serverId, LineServerInstallReqVO r) {
        int slotPortEnd = r.getSlotPortBase() + r.getSlotPoolSize();

        // 用 LinkedHashMap 保留插入顺序便于 debug
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", StrUtil.blankToDefault(serverId, "<unset>"));
        vars.put("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        vars.put("TIMEZONE", r.getTimezone());
        vars.put("INSTALL_UFW", String.valueOf(Boolean.TRUE.equals(r.getInstallUfw())));
        vars.put("XRAY_VERSION", r.getXrayVersion());
        vars.put("XRAY_API_PORT", String.valueOf(r.getXrayApiPort()));
        vars.put("LOG_DIR", r.getLogDir());
        vars.put("SLOT_PORT_BASE", String.valueOf(r.getSlotPortBase()));
        vars.put("SLOT_PORT_END", String.valueOf(slotPortEnd));
        vars.put("SLOT_POOL_SIZE", String.valueOf(r.getSlotPoolSize()));
        return vars;
    }
}
