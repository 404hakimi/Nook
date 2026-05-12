package com.nook.biz.node.service.xray.server;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayServerStatusRespVO;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.server.script.config.RemoteScriptPaths;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.framework.xray.server.snapshot.XrayDaemonExtraSnapshot;
import com.nook.biz.node.service.support.SessionCredentialMapper;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.service.xray.client.XrayTrafficSampleService;
import com.nook.biz.operation.api.dto.EnqueueRequest;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OperationOrchestrator;
import com.nook.biz.operation.api.ProgressSink;
import com.nook.framework.security.stp.StpSystemUtil;
import org.springframework.context.annotation.Lazy;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Xray 线路服务器管理 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayServerManageServiceImpl implements XrayServerManageService {

    @Resource
    private RemoteScriptRunner scriptRunner;
    @Resource
    private ServerProbe serverProbe;
    @Resource
    private XrayDaemonProbe xrayDaemonProbe;
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private SessionCredentialMapper sessionCredentialMapper;
    /** restart 前置 sample 让流量数据不丢; 失败仅 warn, 不阻塞 restart 主流程 */
    @Resource
    private XrayTrafficSampleService trafficSampleService;
    /** @Lazy 破除循环依赖: service → orchestrator → handlerRegistry → handler → service */
    @Lazy
    @Resource
    private OperationOrchestrator operationOrchestrator;
    @Resource
    private OpConfigResolver opConfigResolver;

    @Override
    public void installStreaming(String serverId, XrayServerInstallReqVO reqVO, Consumer<String> lineSink) {
        // 长任务 (1-10 min) 用 INSTALL scope, 跟短任务 SHARED 隔离 cache, 防被 invalidate 半路打断
        SshSession session = sessionCredentialMapper.acquire(serverId, SshSessionScope.INSTALL);
        // logDir 留空时按 <installDir>/logs 派生; vars 与落库都用同一份, 保持一致
        String effectiveLogDir = StrUtil.isBlank(reqVO.getLogDir())
                ? reqVO.getInstallDir() + "/logs"
                : reqVO.getLogDir();
        Map<String, String> vars = buildInstallVars(serverId, reqVO, effectiveLogDir);
        String script = assembleInstallScript(reqVO, vars);
        Duration installTimeout = Duration.ofSeconds(session.cred().getInstallTimeoutSeconds());
        scriptRunner.runScriptStreaming(
                session,
                script,
                RemoteScriptPaths.INSTALL_XRAY_TMP,
                installTimeout,
                lineSink);

        // 装完后把字面 "latest" 解析成具体 tag (如 "v26.3.27"), 让 xray_node 反映远端真实版本;
        // 解析失败 fallback 原值, 不阻断主流程.
        String resolvedVersion = xrayDaemonProbe.resolveActualVersion(session, reqVO.getXrayVersion());
        if (!resolvedVersion.equals(reqVO.getXrayVersion())) {
            lineSink.accept("[nook] xray 实际版本: " + resolvedVersion + " (前端选 "
                    + reqVO.getXrayVersion() + ")\n");
        }

        // 部署完成 → 初始化 nook 内部状态. xrayNodeService.upsertXrayNode 内部同事务初始化 slot 池, 杜绝半初始化态.
        // 失败时事务回滚 + 抛错让 emitter 红色完成, 远端 xray 进程已就绪 (可独立重跑 install 修复 nook 状态).
        try {
            xrayNodeService.upsertXrayNode(
                    serverId,
                    resolvedVersion,
                    reqVO.getXrayApiPort(),
                    reqVO.getInstallDir(),
                    effectiveLogDir,
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
        EnqueueRequest req = EnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.XRAY_RESTART.name())
                .operator(currentOperator())
                .build();
        return operationOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.XRAY_RESTART.name()), String.class);
    }

    /** XRAY_RESTART handler 直接调本方法; package-private 防止业务代码绕过队列直接调用. */
    String doRestart(String serverId, ProgressSink progress) {
        ProgressSink sink = progress == null ? ProgressSink.noop() : progress;
        // restart 是可控的"清零事件" — systemctl restart 后 xray in-memory counter 全归零;
        // 先 sample 一次把当前增量入库, 让用户层流量统计跨重启不丢. 失败仅 warn, 不阻塞 restart.
        sink.report("采样流量入库", 20);
        try {
            trafficSampleService.sampleServerTraffic(serverId);
        } catch (Exception e) {
            log.warn("[restart] server={} 前置 sample 失败, 仍继续 restart: {}", serverId, e.getMessage());
        }
        sink.report("获取 SSH 会话", 40);
        SshSession session = sessionCredentialMapper.acquire(serverId, SshSessionScope.SHARED);
        sink.report("正在执行 systemctl restart", 60);
        String out = xrayDaemonProbe.restart(session);
        sink.report("等待进程就绪", 90);
        return out;
    }

    @Override
    public XrayServerStatusRespVO getXraySystemdStatus(String serverId) {
        SshSession session = sessionCredentialMapper.acquire(serverId, SshSessionScope.SHARED);
        // ServerProbe 只回通用 systemd 状态 (active/uptime/enabled); xray 专属 (version + 监听端口) 走 XrayDaemonProbe
        SystemdStatusSnapshot sysd = serverProbe.readSystemdStatus(session, XrayConstants.SYSTEMD_UNIT);
        int apiPort = xrayNodeService.getXrayNode(serverId).getXrayApiPort();
        XrayDaemonExtraSnapshot extras = xrayDaemonProbe.readExtras(session, apiPort);

        XrayServerStatusRespVO vo = new XrayServerStatusRespVO();
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
        JSONObject params = new JSONObject();
        params.put("enabled", enabled);
        EnqueueRequest req = EnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.SERVER_AUTOSTART.name())
                .operator(currentOperator())
                .paramsJson(params.toJSONString())
                .build();
        return operationOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.SERVER_AUTOSTART.name()), String.class);
    }

    /** SERVER_AUTOSTART handler 调本方法. */
    String doSetAutostart(String serverId, boolean enabled, ProgressSink progress) {
        ProgressSink sink = progress == null ? ProgressSink.noop() : progress;
        sink.report("建立 SSH 会话", 50);
        SshSession session = sessionCredentialMapper.acquire(serverId, SshSessionScope.SHARED);
        sink.report(enabled ? "执行 systemctl enable" : "执行 systemctl disable", 80);
        return xrayDaemonProbe.setAutostart(session, enabled);
    }

    /** 按 reqVO 勾选项把 install 模块拼成完整脚本; 必装 00/50/99, 可选 10-timezone/40-ufw; swap/bbr 不在此链路. */
    private String assembleInstallScript(XrayServerInstallReqVO r, Map<String, String> vars) {
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

    /**
     * 取当前后台登录的 admin id 作 operator; 没有登录态 (定时器 / 系统调用) 退回 "SYSTEM".
     */
    private static String currentOperator() {
        try {
            String id = StpSystemUtil.getLoginIdAsString();
            return StrUtil.blankToDefault(id, "SYSTEM");
        } catch (Exception ignore) {
            return "SYSTEM";
        }
    }

    /** 部署模板渲染变量表; reqVO 字段已被 jakarta @Valid + @AssertTrue 校验, 这里直接拆箱. */
    private Map<String, String> buildInstallVars(String serverId, XrayServerInstallReqVO r, String effectiveLogDir) {
        int slotPortEnd = r.getSlotPortBase() + r.getSlotPoolSize();

        // 用 LinkedHashMap 保留插入顺序便于 debug
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", StrUtil.blankToDefault(serverId, "<unset>"));
        vars.put("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        vars.put("TIMEZONE", r.getTimezone());
        vars.put("INSTALL_UFW", String.valueOf(Boolean.TRUE.equals(r.getInstallUfw())));
        vars.put("XRAY_VERSION", r.getXrayVersion());
        vars.put("XRAY_API_PORT", String.valueOf(r.getXrayApiPort()));
        vars.put("INSTALL_DIR", r.getInstallDir());
        vars.put("LOG_DIR", effectiveLogDir);
        vars.put("LOG_LEVEL", r.getLogLevel());
        vars.put("RESTART_POLICY", r.getRestartPolicy());
        vars.put("ENABLE_ON_BOOT", String.valueOf(Boolean.TRUE.equals(r.getEnableOnBoot())));
        vars.put("FORCE_REINSTALL", String.valueOf(Boolean.TRUE.equals(r.getForceReinstall())));
        vars.put("SLOT_PORT_BASE", String.valueOf(r.getSlotPortBase()));
        vars.put("SLOT_PORT_END", String.valueOf(slotPortEnd));
        vars.put("SLOT_POOL_SIZE", String.valueOf(r.getSlotPoolSize()));
        return vars;
    }
}
