package com.nook.biz.node.service.xray.server.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
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
import com.nook.biz.node.framework.agent.AgentControlClient;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.xray.inbound.InboundProtocol;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolFactory;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionResult;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionRequest;
import com.nook.biz.node.framework.xray.install.XrayInstallScriptAssembler;
import com.nook.biz.node.framework.xray.server.XrayDaemonControl;
import com.nook.biz.node.framework.xray.XrayConstants;
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
import java.time.LocalDateTime;
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
    private ServerProbe serverProbe;
    @Resource
    private XrayInstallService xrayInstallService;
    @Resource
    private XrayInboundService xrayInboundService;
    @Resource
    private XrayInstallScriptAssembler xrayInstallScriptAssembler;
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
        // 协议实现: 校验 + 算形态/参数 (含域名解析/CF A 记录/密钥生成); 加协议只加实现, 不改这里
        InboundProtocol protocol = inboundProtocolFactory.resolve(reqVO);
        protocol.validate(serverId, reqVO);
        // 改客户面参数 (域名/wsPath 等) 仅告警留痕不阻断, 在用客户重拉订阅即可
        xrayInstallValidator.warnIfClientFacingChange(serverId, reqVO);
        // 完整重排: xray 由已装好的 agent 本地部署, 取连接信息 (出网 IP + agent_token)
        ResourceServerDO srv = resourceServerValidator.validateExists(serverId);

        // 协议产出: 形态/语义参数/模板占位符 + 域名 (实现内做域名解析/CF A 记录/密钥生成); 渲染 + 落库共用同一份
        InboundProvisionResult prov = protocol.provision(InboundProvisionRequest.builder()
                .serverId(serverId)
                .reqVO(reqVO)
                .serverIp(srv.getIpAddress())
                .lineSink(lineSink)
                .build());

        // 渲染完整脚本 (拼装下沉到基础设施 XrayInstallScriptAssembler) → 经 agent 控制接口本地执行; agent 流式回传 stdout
        String script = xrayInstallScriptAssembler.assemble(serverId, reqVO, prov);
        agentControlClient.execute(srv.getIpAddress(), srv.getAgentToken(), script, XRAY_DEPLOY_TIMEOUT_SECONDS, lineSink);

        // 部署完成 → 同事务写 xray_install + xray_inbound (两表 1:1); 版本用前端选值 (agent 执行无 SSH session 解析实际 tag)
        try {
            transactionTemplate.executeWithoutResult(txStatus ->
                    persistDeployment(serverId, reqVO, reqVO.getXrayVersion(), prov));
            lineSink.accept("[nook] ✔ DB 已写入\n");
        } catch (RuntimeException e) {
            log.error("[install] xray 部署成功但 DB 状态初始化失败 server={}, 已自动回滚", serverId, e);
            lineSink.accept("[nook] ⚠ 远端部署 OK, 但 DB 状态初始化失败 (已回滚): "
                    + e.getMessage() + " (重新点部署即可幂等修复)\n");
            throw e;
        }
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
        srv.setDomainId(useTls ? inbound.getDomainId() : null);
        srv.setSubdomain(useTls ? inbound.getSubdomain() : null);
        srv.setInstalledAt(LocalDateTime.now());
        xrayInstallService.upsert(srv);

        XrayInboundDO cfg = new XrayInboundDO();
        cfg.setServerId(serverId);
        // 协议形态收敛到 protocol_key (protocol/transport/security 由它解出); 域名/ws/tls/reality 语义全在 params
        cfg.setProtocolKey(prov.getProtocol().getKey());
        cfg.setListenIp(inbound.getListenIp());
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
