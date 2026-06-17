package com.nook.biz.node.service.xray.server;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.api.xray.XrayInstallDefaults;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallRespVO;
import com.nook.biz.node.convert.xray.XrayInstallConvert;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.XrayInboundProtocolDO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.framework.agent.AgentControlClient;
import com.nook.biz.node.framework.cloudflare.CloudflareApiClient;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.xray.inbound.config.InboundTemplateRenderer;
import com.nook.biz.node.framework.xray.inbound.strategy.InboundProtocolStrategy;
import com.nook.biz.node.framework.xray.inbound.strategy.InboundProtocolStrategyFactory;
import com.nook.biz.node.framework.xray.inbound.strategy.InboundProvision;
import com.nook.biz.node.mapper.XrayInboundProtocolMapper;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.service.xray.config.XrayInboundService;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.XrayInstallValidator;
import com.nook.biz.system.api.domain.SystemDomainApi;
import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;
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
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.ssh.script.ScriptModule;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
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
public class XrayInstallManageServiceImpl implements XrayInstallManageService {

    /** agent 本地装 xray 的超时秒数; wget+apt+acme DNS-01 耗时较长, 独立于 SSH 场景超时 (且避免 cred 字段 null 拆箱 NPE). */
    private static final int XRAY_DEPLOY_TIMEOUT_SECONDS = 1200;

    @Resource
    private AgentControlClient agentControlClient;
    @Resource
    private ScriptCatalog scriptCatalog;
    @Resource
    private ServerProbe serverProbe;
    @Resource
    private XrayInstallService xrayInstallService;
    @Resource
    private XrayInboundService xrayInboundService;
    @Resource
    private XrayInboundProtocolMapper xrayInboundProtocolMapper;
    @Resource
    private InboundTemplateRenderer inboundTemplateRenderer;
    @Resource
    private InboundProtocolStrategyFactory inboundProtocolStrategyFactory;
    @Resource
    private XrayInstallValidator xrayInstallValidator;
    @Resource
    private OpOrchestrator opOrchestrator;
    @Resource
    private OpConfigResolver opConfigResolver;
    @Resource
    private CloudflareApiClient cloudflareApiClient;
    @Resource
    private SystemDomainApi systemDomainApi;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private StreamingEndpointSupport streamingEndpointSupport;
    @Resource
    private WebStreamingProperties webStreamingProperties;
    /** 装机 SSH 调用拆事务外, DB 两表写入由 TransactionTemplate 包同事务 (self-invocation @Transactional 不生效) */
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public void installStreaming(String serverId, XrayInstallReqVO reqVO, Consumer<String> lineSink) {
        // 协议策略: 校验 + 算形态/参数; 加协议只加策略, 不改这里
        InboundProtocolStrategy strategy = inboundProtocolStrategyFactory.resolve(reqVO);
        strategy.validate(serverId, reqVO);
        // 改客户面参数 (域名/wsPath 等) 仅告警留痕不阻断, 在用客户重拉订阅即可
        xrayInstallValidator.warnIfClientFacingChange(serverId, reqVO);
        // 完整重排: xray 由已装好的 agent 本地部署, 取连接信息 (出网 IP + agent_token)
        ResourceServerDO srv = resourceServerValidator.validateExists(serverId);
        // 域名绑定 → 走 TLS; 根域 + cfApiToken 从 system_domain 取, 完整 FQDN = 二级标签 + 根域
        boolean useTls = StrUtil.isNotBlank(reqVO.getDomainId());
        SystemDomainRespDTO domain = useTls ? systemDomainApi.getById(reqVO.getDomainId()) : null;
        String fullDomain = useTls ? XrayConstants.fqdn(reqVO.getSubdomain(), domain.getDomain()) : null;

        // 部署前加 A 记录: 仅走域名路径需要; 失败不阻断, 用户可手动在 CF 面板加
        if (useTls && StrUtil.isNotBlank(domain.getCfApiToken())) {
            try {
                cloudflareApiClient.ensureARecord(domain.getCfApiToken(), fullDomain, srv.getIpAddress(), false);
                lineSink.accept("[nook] ✔ Cloudflare A 记录已加: " + fullDomain + " → " + srv.getIpAddress() + "\n");
            } catch (Exception cfe) {
                lineSink.accept("[nook] ⚠ Cloudflare A 记录创建失败 (" + cfe.getMessage()
                        + "), 请手动在 CF 面板加 A 记录\n");
                log.warn("[install] CF API 失败 server={} domainId={}: {}",
                        serverId, reqVO.getDomainId(), cfe.getMessage());
            }
        }

        // 协议产出: 形态 + 语义参数 + 模板占位符 (reality 在此生成密钥, 渲染 + 落库共用)
        InboundProvision prov = strategy.provision(reqVO, fullDomain);

        // 渲染完整脚本 → 经 agent 控制接口本地执行 (取代 SSH); agent 流式回传 stdout
        Map<String, String> vars = buildInstallVars(serverId, reqVO, domain, fullDomain, prov);
        String script = assembleInstallScript(reqVO, vars);
        int installTimeout = XRAY_DEPLOY_TIMEOUT_SECONDS;
        agentControlClient.execute(srv.getIpAddress(), srv.getAgentToken(), script, installTimeout, lineSink);

        // 部署完成 → 同事务写 xray_install + xray_inbound (两表 1:1); 版本用前端选值 (agent 执行无 SSH session 解析实际 tag)
        try {
            transactionTemplate.executeWithoutResult(txStatus ->
                    persistDeployment(serverId, reqVO, reqVO.getXrayVersion(), fullDomain, prov));
            lineSink.accept("[nook] ✔ DB 已写入\n");
        } catch (RuntimeException e) {
            log.error("[install] xray 部署成功但 DB 状态初始化失败 server={}, 已自动回滚", serverId, e);
            lineSink.accept("[nook] ⚠ 远端部署 OK, 但 DB 状态初始化失败 (已回滚): "
                    + e.getMessage() + " (重新点部署即可幂等修复)\n");
            throw e;
        }
    }

    /** 两表写入: 实例元数据 / inbound 配置; caller 必须包事务. params/security 由 caller 算好 (reality 密钥跟脚本渲染同一份). */
    private void persistDeployment(String serverId, XrayInstallReqVO r, String resolvedVersion, String fullDomain,
                                   InboundProvision prov) {
        boolean useTls = StrUtil.isNotBlank(fullDomain);
        boolean isReality = prov.protocol() == XrayInboundProtocolEnum.VLESS_REALITY;
        XrayInstallDO srv = new XrayInstallDO();
        srv.setServerId(serverId);
        srv.setXrayVersion(resolvedVersion);
        // 基础设施路径/端口用后端固定默认 (前端不再传)
        srv.setXrayApiPort(XrayInstallDefaults.API_PORT);
        srv.setXrayInstallDir(XrayInstallDefaults.INSTALL_DIR);
        srv.setXrayBinaryPath(XrayInstallDefaults.XRAY_BINARY_PATH);
        srv.setXrayConfigPath(XrayInstallDefaults.XRAY_CONFIG_PATH);
        srv.setXrayShareDir(XrayInstallDefaults.XRAY_SHARE_DIR);
        srv.setXrayLogDir(XrayInstallDefaults.LOG_DIR);
        srv.setXraySystemdUnitPath(XrayInstallDefaults.SYSTEMD_UNIT_PATH);
        srv.setDomainId(useTls ? r.getDomainId() : null);
        srv.setSubdomain(useTls ? r.getSubdomain() : null);
        srv.setInstalledAt(LocalDateTime.now());
        xrayInstallService.upsert(srv);

        XrayInboundDO cfg = new XrayInboundDO();
        cfg.setServerId(serverId);
        // 协议形态 (protocol/transport/security) 由策略给出
        cfg.setProtocol(prov.protocol().getProtocol());
        cfg.setTransport(prov.protocol().getTransport());
        cfg.setSecurity(prov.protocol().getSecurity());
        cfg.setListenIp(r.getListenIp());
        cfg.setSharedInboundPort(r.getSharedInboundPort());
        // 旧列双写 (步骤②切读后删); reality 不用 ws/tls/domain, 全置空
        cfg.setWsPath(isReality ? null : r.getWsPath());
        cfg.setDomain(isReality ? null : fullDomain);
        cfg.setTlsCertPath(useTls && !isReality ? XrayInstallDefaults.TLS_CERT_PATH : null);
        cfg.setTlsKeyPath(useTls && !isReality ? XrayInstallDefaults.TLS_KEY_PATH : null);
        // 协议语义参数 (含 reality 密钥) 收口到 params
        cfg.setParams(JSON.toJSONString(prov.params()));
        xrayInboundService.upsert(cfg);
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
        XrayInstallDO server = xrayInstallValidator.validateExists(serverId);
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
    private String assembleInstallScript(XrayInstallReqVO r, Map<String, String> vars) {
        List<ScriptModule> modules = new ArrayList<>();
        modules.add(NookScripts.MODULE_PREPARE_ENV);
        if (Boolean.TRUE.equals(r.getSetTimezone())) modules.add(NookScripts.MODULE_TIMEZONE);
        if (Boolean.TRUE.equals(r.getInstallUfw()))  modules.add(NookScripts.MODULE_UFW);
        if (StrUtil.isNotBlank(r.getDomainId()))     modules.add(NookScripts.MODULE_ACME_TLS);
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
    private Map<String, String> buildInstallVars(String serverId, XrayInstallReqVO r, SystemDomainRespDTO domain,
                                                 String fullDomain, InboundProvision prov) {
        boolean useTls = domain != null;
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", StrUtil.blankToDefault(serverId, "<unset>"));
        vars.put("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        vars.put("TIMEZONE", Boolean.TRUE.equals(r.getSetTimezone()) ? "Asia/Shanghai" : "");
        vars.put("INSTALL_UFW", String.valueOf(Boolean.TRUE.equals(r.getInstallUfw())));
        vars.put("XRAY_VERSION", r.getXrayVersion());
        // 基础设施参数 (端口/路径/日志/重启) 用后端固定默认 (前端不再传); agent reconcile 据此探
        vars.put("XRAY_API_PORT", String.valueOf(XrayInstallDefaults.API_PORT));
        vars.put("INSTALL_DIR", XrayInstallDefaults.INSTALL_DIR);
        vars.put("LOG_DIR", XrayInstallDefaults.LOG_DIR);
        vars.put("LOG_LEVEL", XrayInstallDefaults.LOG_LEVEL);
        vars.put("RESTART_POLICY", XrayInstallDefaults.RESTART_POLICY);
        vars.put("ENABLE_ON_BOOT", String.valueOf(Boolean.TRUE.equals(r.getEnableOnBoot())));
        vars.put("FORCE_REINSTALL", String.valueOf(Boolean.TRUE.equals(r.getForceReinstall())));
        vars.put("SHARED_INBOUND_PORT", String.valueOf(r.getSharedInboundPort()));
        vars.put("WS_PATH", r.getWsPath());
        vars.put("XRAY_BINARY_PATH",       XrayInstallDefaults.XRAY_BINARY_PATH);
        vars.put("XRAY_CONFIG_PATH",       XrayInstallDefaults.XRAY_CONFIG_PATH);
        vars.put("XRAY_SHARE_DIR",         XrayInstallDefaults.XRAY_SHARE_DIR);
        vars.put("XRAY_SYSTEMD_UNIT_PATH", XrayInstallDefaults.SYSTEMD_UNIT_PATH);
        vars.put("USE_TLS", String.valueOf(useTls));
        vars.put("DOMAIN", useTls ? StrUtil.blankToDefault(fullDomain, "") : "");
        vars.put("CF_API_TOKEN", useTls ? StrUtil.blankToDefault(domain.getCfApiToken(), "") : "");
        vars.put("TLS_CERT_PATH",  useTls ? XrayInstallDefaults.TLS_CERT_PATH : "");
        vars.put("TLS_KEY_PATH",   useTls ? XrayInstallDefaults.TLS_KEY_PATH : "");
        // in_shared inbound 由协议模板渲染好 base64 下发, 取代脚本 shell 硬编码; 模板 key + 占位符值由策略给出
        XrayInboundProtocolDO template = xrayInboundProtocolMapper.selectById(prov.protocol().getKey());
        String sharedInbound = inboundTemplateRenderer.render(template.getInboundTemplate(), prov.templateVars());
        String sharedInboundB64 = Base64.getEncoder()
                .encodeToString(sharedInbound.getBytes(StandardCharsets.UTF_8));
        vars.put("SHARED_INBOUND_B64", sharedInboundB64);
        return vars;
    }

    @Override
    public XrayInstallRespVO getXrayInstallDetail(String serverId) {
        XrayInstallDO entity = xrayInstallValidator.validateExists(serverId);
        XrayInstallRespVO vo = XrayInstallConvert.INSTANCE.convert(entity);
        Set<String> ids = Set.of(serverId);
        Map<String, ResourceServerDO> serverMap = resourceServerService.getServerMap(ids);
        Map<String, String> hostMap = resourceServerService.getIpAddressMap(ids);
        XrayInstallConvert.fillServer(vo, serverMap, hostMap);
        // 据 domain_id 回填根域并拼完整 FQDN (二级标签 + 根域); 根域被删则留空, 不阻断详情
        if (StrUtil.isNotBlank(vo.getDomainId())) {
            String rootDomain = systemDomainApi.getDomainMap(Set.of(vo.getDomainId())).get(vo.getDomainId());
            if (StrUtil.isNotBlank(rootDomain)) {
                vo.setDomain(XrayConstants.fqdn(vo.getSubdomain(), rootDomain));
            }
        }
        return vo;
    }

    @Override
    public ResponseBodyEmitter installXrayStream(String serverId, XrayInstallReqVO reqVO) {
        resourceServerValidator.validateExists(serverId);
        int installTimeout = XRAY_DEPLOY_TIMEOUT_SECONDS;
        Duration emitterTimeout = Duration.ofSeconds(installTimeout)
                .plus(webStreamingProperties.getEmitterBuffer());
        return streamingEndpointSupport.stream("install:" + serverId, emitterTimeout,
                lineSink -> installStreaming(serverId, reqVO, lineSink));
    }
}
