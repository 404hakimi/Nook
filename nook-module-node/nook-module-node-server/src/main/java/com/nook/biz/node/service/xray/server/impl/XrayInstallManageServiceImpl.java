package com.nook.biz.node.service.xray.server.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.api.enums.XrayInstallStatusEnum;
import com.nook.biz.node.api.xray.XrayInstallDefaults;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.xray.vo.XrayInboundConfigVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallRespVO;
import com.nook.biz.node.convert.xray.XrayInstallConvert;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.framework.acme.IssuedCert;
import com.nook.biz.node.framework.acme.XrayCertManager;
import com.nook.biz.node.framework.agent.AgentControlClient;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.xray.inbound.InboundProtocol;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolFactory;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionResult;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionRequest;
import com.nook.biz.node.framework.xray.inbound.InboundSetupSpec;
import com.nook.biz.node.framework.xray.install.XrayDeployRequest;
import com.nook.biz.node.framework.xray.server.XrayDaemonControl;
import com.nook.biz.system.api.domain.DomainUtils;
import com.nook.biz.node.service.xray.config.XrayInboundService;
import com.nook.biz.node.service.xray.server.XrayInstallManageService;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.XrayInstallValidator;
import com.nook.biz.system.api.domain.SystemDomainApi;
import com.nook.framework.web.StreamingEndpointSupport;
import com.nook.framework.web.WebStreamingProperties;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
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

    /** agent 本地装 xray 的超时秒数 (下载 binary 为主; 证书已由后台签好下发), 独立于 SSH 场景超时 (且避免 cred 字段 null 拆箱 NPE). */
    private static final int XRAY_DEPLOY_TIMEOUT_SECONDS = 1200;

    @Resource
    private AgentControlClient agentControlClient;
    @Resource
    private XrayCertManager xrayCertManager;
    @Resource
    private ServerProbe serverProbe;
    @Resource
    private XrayInstallService xrayInstallService;
    @Resource
    private XrayInboundService xrayInboundService;
    @Resource
    private InboundProtocolFactory inboundProtocolFactory;
    @Resource
    private XrayInstallValidator xrayInstallValidator;
    @Resource
    private XrayDaemonControl xrayDaemonControl;
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
        // controller VO 在此映射成 framework 中立入站规格, 协议实现不再认识 controller VO
        InboundSetupSpec spec = toSetupSpec(reqVO.getInbound());
        // 协议实现: 校验 + 算形态/参数 (含域名解析/CF A 记录/密钥生成); 加协议只加实现, 不改这里
        InboundProtocol protocol = inboundProtocolFactory.resolve(spec);
        protocol.validate(serverId, spec);
        // 改客户面参数 (域名/wsPath 等) 仅告警留痕不阻断, 在用客户重拉订阅即可
        xrayInstallValidator.warnIfClientFacingChange(serverId, spec);
        // 完整重排: xray 由已装好的 agent 本地部署, 取连接信息 (出网 IP + agent_token)
        ResourceServerDO srv = resourceServerValidator.validateExists(serverId);

        // 协议产出: 形态/语义参数 + 渲染好的 inbound JSON + 域名 (实现内做域名解析/CF A 记录/密钥生成)
        InboundProvisionResult prov = protocol.provision(InboundProvisionRequest.builder()
                .serverId(serverId)
                .spec(spec)
                .serverIp(srv.getIpAddress())
                .lineSink(lineSink)
                .build());

        // 配置先行 (DB 为中心): 先把期望态落库 (status=deploying), 再通知 agent 装机
        transactionTemplate.executeWithoutResult(txStatus ->
                persistDeployment(serverId, reqVO, reqVO.getXrayVersion(), prov));
        lineSink.accept("[nook] ✔ 配置已落库\n");

        // 绑域名: 后台签发/复用 TLS 证书 (DNS-01 经 Cloudflare), 随部署请求下发给 agent 写盘; agent 不再直连 CF/acme.
        // 放事务外: 签发是较慢网络操作 (~30-60s), 不能占着 DB 事务.
        IssuedCert cert = null;
        if (StrUtil.isNotBlank(prov.getFullDomain())) {
            lineSink.accept("[nook] → 后台签发/复用 TLS 证书 (DNS-01)...\n");
            // 透出签发各阶段进度行 (兼流式心跳: 签发可能 >60s, 否则中间代理 idle 易断流)
            cert = xrayCertManager.ensureCert(serverId, prov.getFullDomain(), prov.getCfApiToken(),
                    msg -> lineSink.accept("[nook]   " + msg + "\n"));
            lineSink.accept("[nook] ✔ 证书就绪 (到期 " + cert.getNotAfter() + ")\n");
        }
        lineSink.accept("[nook] 通知 agent 装机...\n");

        // 通知 agent: 下发结构化配置 (版本/开关/inbound JSON/域名/证书), agent 用内置逻辑本地装机 + 流式回传日志
        XrayDeployRequest deployReq = buildDeployRequest(serverId, reqVO, prov, cert);
        try {
            agentControlClient.deployXray(srv.getIpAddress(), srv.getAgentToken(), deployReq, lineSink);
            xrayInstallService.markInstallStatus(serverId, XrayInstallStatusEnum.OK);
            lineSink.accept("[nook] ✔ agent 部署完成\n");
        } catch (RuntimeException e) {
            xrayInstallService.markInstallStatus(serverId, XrayInstallStatusEnum.FAILED);
            log.error("[install] xray 部署失败 server={} (配置已留库 status=failed, 重新部署幂等修复)", serverId, e);
            lineSink.accept("[nook] ⚠ agent 部署失败: " + e.getMessage() + " (配置已留库, 重新部署即可)\n");
            throw e;
        }
    }

    /** controller 入站 VO → framework 中立入站规格 (协议实现据此工作, 不依赖 controller VO). 协议特定字段在多态 params 里整体透传, 加协议本方法零改. */
    private static InboundSetupSpec toSetupSpec(XrayInboundConfigVO in) {
        return InboundSetupSpec.builder()
                .protocol(in.getProtocol())
                .sharedInboundPort(in.getSharedInboundPort())
                .params(in.getParams())
                .build();
    }

    /** 后台配置 → agent 装机请求 (结构化下发; agent 用内置逻辑本地装机, inbound JSON 不透明写盘, TLS 证书后台签好直接下发). */
    private XrayDeployRequest buildDeployRequest(String serverId, XrayInstallReqVO r, InboundProvisionResult prov, IssuedCert cert) {
        return XrayDeployRequest.builder()
                .serverId(serverId)
                .xrayVersion(r.getXrayVersion())
                .forceReinstall(Boolean.TRUE.equals(r.getForceReinstall()))
                .enableOnBoot(Boolean.TRUE.equals(r.getEnableOnBoot()))
                .installUfw(Boolean.TRUE.equals(r.getInstallUfw()))
                .setTimezone(Boolean.TRUE.equals(r.getSetTimezone()))
                .logRotate(Boolean.TRUE.equals(r.getLogRotate()))
                .sharedInboundPort(r.getInbound().getSharedInboundPort())
                .inboundConfigJson(prov.getInboundJson())
                .domain(prov.getFullDomain())
                .tlsCertPem(cert != null ? cert.getCertPem() : null)
                .tlsKeyPem(cert != null ? cert.getKeyPem() : null)
                .timeoutSeconds(XRAY_DEPLOY_TIMEOUT_SECONDS)
                .build();
    }

    /** 两表写入: 实例元数据 / inbound 配置; caller 必须包事务. params/security 由 caller 算好 (reality 密钥跟脚本渲染同一份). */
    private void persistDeployment(String serverId, XrayInstallReqVO r, String resolvedVersion, InboundProvisionResult prov) {
        String fullDomain = prov.getFullDomain();
        boolean useTls = StrUtil.isNotBlank(fullDomain);
        XrayInboundConfigVO inbound = r.getInbound();
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
        // 协议特定的域名绑定从 provision 结果取 (协议自己产出), 不再从输入 VO 取协议特化字段
        srv.setDomainId(prov.getDomainId());
        srv.setSubdomain(prov.getSubdomain());
        // 配置先行: 落库即 deploying; installedAt 等 agent 回报成功才置 (见 markInstallStatus)
        srv.setInstallStatus(XrayInstallStatusEnum.DEPLOYING.getCode());
        xrayInstallService.upsert(srv);
        // 重新部署成非 TLS: 全局 NOT_NULL 策略不会把上面置 null 的 domain/证书列写回, 显式清掉避免旧证书/旧域名残留
        if (!useTls) {
            xrayInstallService.clearTlsBinding(serverId);
        }

        XrayInboundDO cfg = new XrayInboundDO();
        cfg.setServerId(serverId);
        // 协议形态收敛到 protocol_key (protocol/transport/security 由它解出); 域名/ws/tls/reality 语义全在 params
        cfg.setProtocolKey(prov.getProtocol().getKey());
        cfg.setSharedInboundPort(inbound.getSharedInboundPort());
        cfg.setParams(JSON.toJSONString(prov.getParams()));
        xrayInboundService.upsert(cfg);
    }

    @Override
    public String restart(String serverId) {
        // 守护进程级命令式操作: 直接 SSH 下发 systemctl restart + 探活回 stdout (脱离 op 队列, "立即重启"是一次性命令, 非声明式期望态)
        XrayInstallDO server = xrayInstallValidator.validateExists(serverId);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        return xrayDaemonControl.restart(session, server.getXrayBinaryPath());
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
        // 直接 SSH 下发 systemctl enable/disable + is-enabled 回 stdout (脱离 op 队列)
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        return xrayDaemonControl.setAutostart(session, enabled);
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
                vo.setDomain(DomainUtils.buildFqdn(vo.getSubdomain(), rootDomain));
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
