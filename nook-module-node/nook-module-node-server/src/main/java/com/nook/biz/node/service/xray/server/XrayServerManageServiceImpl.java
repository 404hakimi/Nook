package com.nook.biz.node.service.xray.server;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayServerStatusRespVO;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.framework.ssh.script.RemoteScriptRunner;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.ssh.script.ScriptModule;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.cloudflare.CloudflareApiClient;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.framework.xray.server.snapshot.XrayDaemonExtraSnapshot;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.XrayNodeValidator;
import com.nook.biz.operation.api.dto.OpEnqueueRequest;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpOrchestrator;
import com.nook.framework.security.stp.StpSystemUtil;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Xray 服务器管理 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayServerManageServiceImpl implements XrayServerManageService {

    private final RemoteScriptRunner scriptRunner;
    private final ScriptCatalog scriptCatalog;
    private final ServerProbe serverProbe;
    private final XrayDaemonProbe xrayDaemonProbe;
    private final XrayNodeService xrayNodeService;
    private final XrayNodeValidator xrayNodeValidator;
    private final OpOrchestrator opOrchestrator;
    private final OpConfigResolver opConfigResolver;
    private final CloudflareApiClient cloudflareApiClient;
    private final ResourceServerValidator resourceServerValidator;
    private final ResourceServerCredentialService credentialService;

    @Override
    public void installStreaming(String serverId, XrayServerInstallReqVO reqVO, Consumer<String> lineSink) {
        // fail-fast 校验全交给 validator 层 (跨字段 + 跟现有客户冲突)
        xrayNodeValidator.validateInstallReq(reqVO);
        xrayNodeValidator.validateAgainstActiveClients(serverId, reqVO);
        boolean useTls = Boolean.TRUE.equals(reqVO.getUseTls());

        // 部署前加 A 记录: 仅走域名路径需要; 失败不阻断, 用户可手动在 CF 面板加
        if (useTls && StrUtil.isNotBlank(reqVO.getCfApiToken())) {
            try {
                resourceServerValidator.validateExists(serverId);
                String serverHost = credentialService.requireByServerId(serverId).getHost();
                cloudflareApiClient.ensureARecord(reqVO.getCfApiToken(), reqVO.getDomain(), serverHost, false);
                lineSink.accept("[nook] ✔ Cloudflare A 记录已加: " + reqVO.getDomain() + " → " + serverHost + "\n");
            } catch (Exception cfe) {
                lineSink.accept("[nook] ⚠ Cloudflare A 记录创建失败 (" + cfe.getMessage()
                        + "), 请手动在 CF 面板加 A 记录\n");
                log.warn("[install] CF API 失败 server={} domain={}: {}",
                        serverId, reqVO.getDomain(), cfe.getMessage());
            }
        }

        // 长任务 (1-10 min) 用 INSTALL scope, 跟短任务 SHARED 隔离 cache, 防被 invalidate 半路打断
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.INSTALL);
        Map<String, String> vars = buildInstallVars(serverId, reqVO);
        String script = assembleInstallScript(reqVO, vars);
        Duration installTimeout = Duration.ofSeconds(session.cred().getInstallTimeoutSeconds());
        scriptRunner.runScriptStreaming(
                session,
                script,
                NookScripts.INSTALL_XRAY_TMP_PREFIX,
                installTimeout,
                lineSink);

        // 装完后把字面 "latest" 解析成具体 tag (如 "v26.3.27"), 让 xray_node 反映远端真实版本;
        // 解析失败 fallback 原值, 不阻断主流程.
        String resolvedVersion = xrayDaemonProbe.resolveActualVersion(session, reqVO.getXrayBinaryPath(), reqVO.getXrayVersion());
        if (!resolvedVersion.equals(reqVO.getXrayVersion())) {
            lineSink.accept("[nook] xray 实际版本: " + resolvedVersion + " (前端选 "
                    + reqVO.getXrayVersion() + ")\n");
        }

        // 部署完成 → 写 xray_node. (跟现有客户冲突的参数变更已在 installStreaming 开头被拦, 这里直接写)
        // 失败时回滚 DB + 抛错让 emitter 红色完成, 远端 xray 已就绪可独立重跑 install 幂等修复.
        try {
            // 不走域名 → cert/key/domain 全 NULL; 走域名 → 用 reqVO 传上来的路径 (前端默认 <installDir>/tls/...)
            String tlsCertPath = useTls ? reqVO.getTlsCertPath() : null;
            String tlsKeyPath  = useTls ? reqVO.getTlsKeyPath()  : null;
            String persistedDomain = useTls ? reqVO.getDomain() : null;
            xrayNodeService.upsertXrayNode(
                    serverId,
                    resolvedVersion,
                    reqVO.getXrayApiPort(),
                    reqVO.getInstallDir(),
                    reqVO.getXrayBinaryPath(),
                    reqVO.getXrayConfigPath(),
                    reqVO.getXrayShareDir(),
                    reqVO.getLogDir(),
                    reqVO.getTouchdownSize(),
                    reqVO.getProtocol(),
                    reqVO.getTransport(),
                    reqVO.getListenIp(),
                    reqVO.getSharedInboundPort(),
                    reqVO.getWsPath(),
                    persistedDomain,
                    tlsCertPath,
                    tlsKeyPath);
            lineSink.accept("[nook] ✔ xray_node 已写入 (touchdownSize=" + reqVO.getTouchdownSize() + ")\n");
        } catch (RuntimeException e) {
            log.error("[install] xray 部署成功但 nook 状态初始化失败 server={}, 已自动回滚 DB", serverId, e);
            lineSink.accept("[nook] ⚠ 远端部署 OK, 但 nook 状态初始化失败 (DB 已回滚): "
                    + e.getMessage() + " (重新点部署即可幂等修复)\n");
            throw e;
        }
    }

    @Override
    public String restart(String serverId) {
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.XRAY_RESTART.name())
                .operator(StpSystemUtil.getLoginIdOrSystem())
                .build();
        return opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.XRAY_RESTART.name()), String.class);
    }

    @Override
    public XrayServerStatusRespVO getXraySystemdStatus(String serverId) {
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        // ServerProbe 只回通用 systemd 状态 (active/uptime/enabled); xray 专属 (version + 监听端口) 走 XrayDaemonProbe
        SystemdStatusSnapshot sysd = serverProbe.readSystemdStatus(session, XrayConstants.SYSTEMD_UNIT);
        XrayNodeDO node = xrayNodeValidator.validateExists(serverId);
        XrayDaemonExtraSnapshot extras = xrayDaemonProbe.readExtras(session, node.getXrayBinaryPath(), node.getXrayApiPort());

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
    public ServiceLogRespVO getXrayLogFile(String serverId, String variant, Integer lines, String keyword) {
        // variant 限定白名单: access / error; 防止前端瞎传, 也兼任路径拼装
        String safeVariant = "error".equalsIgnoreCase(variant) ? "error" : "access";
        XrayNodeDO node = xrayNodeValidator.validateExists(serverId);
        if (StrUtil.isBlank(node.getXrayLogDir())) {
            // 老 node 没填 logDir 时返回空日志, 不抛错 (前端能识别空状态)
            ServiceLogRespVO empty = new ServiceLogRespVO();
            empty.setUnit("(xray_log_dir 未配置)");
            empty.setLines(0);
            empty.setLevel("file");
            empty.setLog("");
            return empty;
        }
        String filePath = node.getXrayLogDir().replaceAll("/+$", "") + "/" + safeVariant + ".log";
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        JournalLogSnapshot snap = serverProbe.readFileLog(session, filePath, lines, keyword);

        ServiceLogRespVO vo = new ServiceLogRespVO();
        vo.setUnit(snap.getUnit());
        vo.setLines(snap.getLines());
        vo.setLevel(snap.getLevel());
        vo.setKeyword(snap.getKeyword());
        vo.setLog(snap.getLog());
        return vo;
    }

    @Override
    public String setAutostart(String serverId, boolean enabled) {
        JSONObject params = new JSONObject();
        params.put("enabled", enabled);
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.SERVER_AUTOSTART.name())
                .operator(StpSystemUtil.getLoginIdOrSystem())
                .paramsJson(params.toJSONString())
                .build();
        return opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.SERVER_AUTOSTART.name()), String.class);
    }

    /**
     * 按 reqVO 勾选项把 install 模块拼成完整脚本.
     *
     * <p>必装: 00-prepare-env / 50-xray / 99-finalize.
     * 可选: 10-timezone (setTimezone=true) / 40-ufw (installUfw=true) / 45-acme-tls (useTls=true).
     * swap / bbr 等通用 OS 调优不在此链路, 走 ServerOps 接口独立触发.
     */
    private String assembleInstallScript(XrayServerInstallReqVO r, Map<String, String> vars) {
        List<ScriptModule> modules = new java.util.ArrayList<>();
        modules.add(NookScripts.MODULE_PREPARE_ENV);
        if (Boolean.TRUE.equals(r.getSetTimezone())) modules.add(NookScripts.MODULE_TIMEZONE);
        if (Boolean.TRUE.equals(r.getInstallUfw()))  modules.add(NookScripts.MODULE_UFW);
        if (Boolean.TRUE.equals(r.getUseTls()))      modules.add(NookScripts.MODULE_ACME_TLS);
        if (Boolean.TRUE.equals(r.getLogRotate()))   modules.add(NookScripts.MODULE_LOGROTATE);
        modules.add(NookScripts.MODULE_XRAY);
        modules.add(NookScripts.MODULE_FINALIZE);
        return scriptCatalog.assemble(modules, vars);
    }

    /**
     * 部署模板渲染变量表; reqVO 字段已被 jakarta @Valid 校验, 这里只做拆箱 + 转 string, **零派生**.
     * 所有路径完全来自前端 (xrayBinaryPath / xrayConfigPath / ...), 后端不做任何拼接.
     */
    private Map<String, String> buildInstallVars(String serverId, XrayServerInstallReqVO r) {
        boolean useTls = Boolean.TRUE.equals(r.getUseTls());
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", StrUtil.blankToDefault(serverId, "<unset>"));
        vars.put("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        // setTimezone=true 才会渲染 10-timezone 模块; 写死 Asia/Shanghai (开关只有"开 / 不开"两态)
        vars.put("TIMEZONE", Boolean.TRUE.equals(r.getSetTimezone()) ? "Asia/Shanghai" : "");
        vars.put("INSTALL_UFW", String.valueOf(Boolean.TRUE.equals(r.getInstallUfw())));
        vars.put("XRAY_VERSION", r.getXrayVersion());
        vars.put("XRAY_API_PORT", String.valueOf(r.getXrayApiPort()));
        vars.put("INSTALL_DIR", r.getInstallDir());
        vars.put("LOG_DIR", r.getLogDir());
        vars.put("LOG_LEVEL", r.getLogLevel());
        vars.put("RESTART_POLICY", r.getRestartPolicy());
        vars.put("ENABLE_ON_BOOT", String.valueOf(Boolean.TRUE.equals(r.getEnableOnBoot())));
        vars.put("FORCE_REINSTALL", String.valueOf(Boolean.TRUE.equals(r.getForceReinstall())));
        vars.put("TOUCHDOWN_SIZE", String.valueOf(r.getTouchdownSize()));
        vars.put("SHARED_INBOUND_PORT", String.valueOf(r.getSharedInboundPort()));
        vars.put("WS_PATH", r.getWsPath());
        // 全部 xray 安装路径前端传上来, 这里平铺到模板; 父目录由脚本 dirname 派生.
        vars.put("XRAY_BINARY_PATH",       r.getXrayBinaryPath());
        vars.put("XRAY_CONFIG_PATH",       r.getXrayConfigPath());
        vars.put("XRAY_SHARE_DIR",         r.getXrayShareDir());
        vars.put("XRAY_SYSTEMD_UNIT_PATH", XrayConstants.SYSTEMD_UNIT_PATH);
        // TLS 三件套: useTls=false 时全部空串, 模板里 USE_TLS 决定走不走 TLS 块
        vars.put("USE_TLS", String.valueOf(useTls));
        vars.put("DOMAIN", useTls ? StrUtil.blankToDefault(r.getDomain(), "") : "");
        vars.put("CF_API_TOKEN", useTls ? StrUtil.blankToDefault(r.getCfApiToken(), "") : "");
        vars.put("TLS_CERT_PATH",  useTls ? StrUtil.blankToDefault(r.getTlsCertPath(), "") : "");
        vars.put("TLS_KEY_PATH",   useTls ? StrUtil.blankToDefault(r.getTlsKeyPath(),  "") : "");
        return vars;
    }
}
