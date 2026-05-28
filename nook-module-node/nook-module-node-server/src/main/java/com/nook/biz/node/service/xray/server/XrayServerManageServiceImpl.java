package com.nook.biz.node.service.xray.server;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayServerRespVO;
import com.nook.biz.node.convert.xray.XrayServerConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.framework.cloudflare.CloudflareApiClient;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.biz.node.service.xray.server.XrayServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.XrayServerValidator;
import com.nook.framework.web.StreamingEndpointSupport;
import com.nook.framework.web.WebStreamingProperties;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.dto.OpEnqueueRequest;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.spi.OpOrchestrator;
import com.nook.framework.security.stp.StpSystemUtil;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.RemoteScriptRunner;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.ssh.script.ScriptModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final XrayServerService xrayServerService;
    private final XrayConfigService xrayConfigService;
    private final XrayServerValidator xrayServerValidator;
    private final ResourceServerCapacityMapper capacityMapper;
    private final OpOrchestrator opOrchestrator;
    private final OpConfigResolver opConfigResolver;
    private final CloudflareApiClient cloudflareApiClient;
    private final ResourceServerValidator resourceServerValidator;
    private final ResourceServerCredentialService credentialService;
    private final ResourceServerService resourceServerService;
    private final StreamingEndpointSupport streamingSupport;
    private final WebStreamingProperties webStreamingProperties;
    /** 装机 SSH 调用拆事务外, DB 三表写入由 TransactionTemplate 包同事务 (self-invocation @Transactional 不生效) */
    private final TransactionTemplate transactionTemplate;

    @Override
    public void installStreaming(String serverId, XrayServerInstallReqVO reqVO, Consumer<String> lineSink) {
        // fail-fast 校验全交给 validator 层 (跨字段 + 跟现有客户冲突)
        xrayServerValidator.validateInstallReq(reqVO);
        xrayServerValidator.validateAgainstActiveClients(serverId, reqVO);
        boolean useTls = Boolean.TRUE.equals(reqVO.getUseTls());

        // 部署前加 A 记录: 仅走域名路径需要; 失败不阻断, 用户可手动在 CF 面板加
        if (useTls && StrUtil.isNotBlank(reqVO.getCfApiToken())) {
            try {
                String serverHost = resourceServerValidator.validateExists(serverId).getIpAddress();
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

        // 装完后把字面 "latest" 解析成具体 tag (如 "v26.3.27"), 让 DB 反映远端真实版本;
        // 解析失败 fallback 原值, 不阻断主流程.
        String resolvedVersion = xrayDaemonProbe.resolveActualVersion(session, reqVO.getXrayBinaryPath(), reqVO.getXrayVersion());
        if (!resolvedVersion.equals(reqVO.getXrayVersion())) {
            lineSink.accept("[nook] xray 实际版本: " + resolvedVersion + " (前端选 "
                    + reqVO.getXrayVersion() + ")\n");
        }

        // 部署完成 → 同事务写 xray_server + xray_config + capacity.client_max_count (三表 1:1)
        // 用 TransactionTemplate 而非 @Transactional self-invocation, 避免 AOP 代理失效
        try {
            transactionTemplate.executeWithoutResult(txStatus -> persistDeployment(serverId, reqVO, resolvedVersion, useTls));
            lineSink.accept("[nook] ✔ DB 已写入 (clientMaxCount=" + reqVO.getClientMaxCount() + ")\n");
        } catch (RuntimeException e) {
            log.error("[install] xray 部署成功但 DB 状态初始化失败 server={}, 已自动回滚", serverId, e);
            lineSink.accept("[nook] ⚠ 远端部署 OK, 但 DB 状态初始化失败 (已回滚): "
                    + e.getMessage() + " (重新点部署即可幂等修复)\n");
            throw e;
        }
    }

    /** 三表写入: 实例元数据 / inbound 配置 / 客户数硬上限; caller 必须包事务. */
    private void persistDeployment(String serverId, XrayServerInstallReqVO r, String resolvedVersion, boolean useTls) {
        XrayServerDO srv = new XrayServerDO();
        srv.setServerId(serverId);
        srv.setXrayVersion(resolvedVersion);
        srv.setXrayApiPort(r.getXrayApiPort());
        srv.setXrayInstallDir(r.getInstallDir());
        srv.setXrayBinaryPath(r.getXrayBinaryPath());
        srv.setXrayConfigPath(r.getXrayConfigPath());
        srv.setXrayShareDir(r.getXrayShareDir());
        srv.setXrayLogDir(r.getLogDir());
        srv.setXraySystemdUnitPath(r.getXraySystemdUnitPath());
        srv.setInstalledAt(LocalDateTime.now());
        xrayServerService.upsert(srv);

        XrayConfigDO cfg = new XrayConfigDO();
        cfg.setServerId(serverId);
        cfg.setProtocol(r.getProtocol());
        cfg.setTransport(r.getTransport());
        cfg.setListenIp(r.getListenIp());
        cfg.setSharedInboundPort(r.getSharedInboundPort());
        cfg.setWsPath(r.getWsPath());
        // useTls=false 时 cert/key/domain 全 NULL; useTls=true 才写
        cfg.setDomain(useTls ? r.getDomain() : null);
        cfg.setTlsCertPath(useTls ? r.getTlsCertPath() : null);
        cfg.setTlsKeyPath(useTls ? r.getTlsKeyPath() : null);
        xrayConfigService.upsert(cfg);

        // 客户数硬上限直接走 capacity.client_max_count; capacity 行由 server 创建路径已占位
        capacityMapper.updateQuota(serverId, null, null, r.getClientMaxCount(), null);
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
    public ServiceLogRespVO getXrayLogFile(String serverId, String variant, Integer lines, String keyword) {
        // variant 限定白名单: access / error; 防前端瞎传, 也兼任路径拼装
        String safeVariant = "error".equalsIgnoreCase(variant) ? "error" : "access";
        XrayServerDO server = xrayServerValidator.validateExists(serverId);
        if (StrUtil.isBlank(server.getXrayLogDir())) {
            // 没填 logDir 时返回空日志, 不抛错 (前端能识别空状态)
            ServiceLogRespVO empty = new ServiceLogRespVO();
            empty.setUnit("(xray_log_dir 未配置)");
            empty.setLines(0);
            empty.setLevel("file");
            empty.setLog("");
            return empty;
        }
        String filePath = server.getXrayLogDir().replaceAll("/+$", "") + "/" + safeVariant + ".log";
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
     */
    private String assembleInstallScript(XrayServerInstallReqVO r, Map<String, String> vars) {
        List<ScriptModule> modules = new ArrayList<>();
        modules.add(NookScripts.MODULE_PREPARE_ENV);
        if (Boolean.TRUE.equals(r.getSetTimezone())) modules.add(NookScripts.MODULE_TIMEZONE);
        if (Boolean.TRUE.equals(r.getInstallUfw()))  modules.add(NookScripts.MODULE_UFW);
        if (Boolean.TRUE.equals(r.getUseTls()))      modules.add(NookScripts.MODULE_ACME_TLS);
        if (Boolean.TRUE.equals(r.getLogRotate()))   modules.add(NookScripts.MODULE_LOGROTATE);
        // journald 容量上限是系统级安全网, 防 service stderr/启停日志撑爆磁盘; 无条件加
        modules.add(NookScripts.MODULE_JOURNALD_CAP);
        modules.add(NookScripts.MODULE_XRAY);
        modules.add(NookScripts.MODULE_FINALIZE);
        return scriptCatalog.assemble(modules, vars);
    }

    /**
     * 部署模板渲染变量表; reqVO 字段已被 jakarta @Valid 校验, 这里只做拆箱 + 转 string, 零派生.
     */
    private Map<String, String> buildInstallVars(String serverId, XrayServerInstallReqVO r) {
        boolean useTls = Boolean.TRUE.equals(r.getUseTls());
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", StrUtil.blankToDefault(serverId, "<unset>"));
        vars.put("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
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
        vars.put("CLIENT_MAX_COUNT", String.valueOf(r.getClientMaxCount()));
        vars.put("SHARED_INBOUND_PORT", String.valueOf(r.getSharedInboundPort()));
        vars.put("WS_PATH", r.getWsPath());
        vars.put("XRAY_BINARY_PATH",       r.getXrayBinaryPath());
        vars.put("XRAY_CONFIG_PATH",       r.getXrayConfigPath());
        vars.put("XRAY_SHARE_DIR",         r.getXrayShareDir());
        vars.put("XRAY_SYSTEMD_UNIT_PATH", r.getXraySystemdUnitPath());
        vars.put("USE_TLS", String.valueOf(useTls));
        vars.put("DOMAIN", useTls ? StrUtil.blankToDefault(r.getDomain(), "") : "");
        vars.put("CF_API_TOKEN", useTls ? StrUtil.blankToDefault(r.getCfApiToken(), "") : "");
        vars.put("TLS_CERT_PATH",  useTls ? StrUtil.blankToDefault(r.getTlsCertPath(), "") : "");
        vars.put("TLS_KEY_PATH",   useTls ? StrUtil.blankToDefault(r.getTlsKeyPath(),  "") : "");
        return vars;
    }

    @Override
    public XrayServerRespVO getXrayServerDetail(String serverId) {
        XrayServerDO entity = xrayServerValidator.validateExists(serverId);
        XrayServerRespVO vo = XrayServerConvert.INSTANCE.convert(entity);
        Set<String> ids = Collections.singleton(serverId);
        Map<String, ResourceServerDO> serverMap = resourceServerService.getServerMap(ids);
        Map<String, String> hostMap = resourceServerService.getIpAddressMap(ids);
        XrayServerConvert.fillServer(vo, serverMap, hostMap);
        return vo;
    }

    @Override
    public ResponseBodyEmitter installXrayStream(String serverId, XrayServerInstallReqVO reqVO) {
        resourceServerValidator.validateExists(serverId);
        int installTimeout = credentialService.requireByServerId(serverId).getInstallTimeoutSeconds();
        Duration emitterTimeout = Duration.ofSeconds(installTimeout)
                .plus(webStreamingProperties.getEmitterBuffer());
        return streamingSupport.stream("install:" + serverId, emitterTimeout,
                lineSink -> installStreaming(serverId, reqVO, lineSink));
    }
}
