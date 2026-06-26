package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingDeployReqVO;
import com.nook.biz.node.controller.resource.vo.landing.Socks5TestRespVO;
import com.nook.biz.node.convert.resource.LandingSocksOpsConvert;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.framework.agent.AgentControlClient;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.socks5.install.Socks5DeployRequest;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.mapper.Socks5InstallMapper;
import com.nook.biz.node.service.resource.ResourceServerLandingSocksOpsService;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.web.StreamingEndpointSupport;
import com.nook.framework.web.WebStreamingProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * SOCKS5 落地节点运维 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerLandingSocksOpsServiceImpl implements ResourceServerLandingSocksOpsService {

    /** dante 的 systemd unit 名 (apt 包默认); 运维命令 (自启/日志) 用. */
    private static final String DANTE_UNIT = "danted";

    /** agent 本地装 dante 的超时秒数 (apt 装包为主, 比 xray 轻); 兼作流式 emitter 超时基数. */
    private static final int SOCKS5_DEPLOY_TIMEOUT_SECONDS = 600;

    @Resource
    private AgentControlClient agentControlClient;
    @Resource
    private Socks5Prober socks5Prober;
    @Resource
    private ServerProbe serverProbe;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ResourceServerLandingValidator resourceServerLandingValidator;
    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private Socks5InstallMapper socks5InstallMapper;
    @Resource
    private ResourceServerCredentialMapper resourceServerCredentialMapper;
    @Resource
    private StreamingEndpointSupport streamingEndpointSupport;
    @Resource
    private WebStreamingProperties webStreamingProperties;

    @Override
    public ResponseBodyEmitter installSocks5Stream(String serverId, ServerLandingDeployReqVO reqVO) {
        // 校验 server / landing 子表存在; SSH 凭据 (含 sshPort 给 UFW 放行) 就绪
        ResourceServerDO server = resourceServerValidator.validateExists(serverId);
        resourceServerLandingValidator.validateExists(serverId);
        ResourceServerCredentialDO cred = resourceServerCredentialMapper.selectById(serverId);
        resourceServerLandingValidator.validateSshCredentialReady(server, cred);
        // 装机配置 (期望态) 写回 landing 子表; 重读 DO 保证下发的是最新值, 服务器重置后据此可重建
        this.applyDeployConfig(serverId, reqVO);
        Socks5InstallDO landing = socks5InstallMapper.selectByServerId(serverId);
        resourceServerLandingValidator.validateSocks5ConfigReady(landing);
        // 开流式 emitter (装机走 agent 控制通道, 超时用本模块常量)
        Duration emitterTimeout = Duration.ofSeconds(SOCKS5_DEPLOY_TIMEOUT_SECONDS)
                .plus(webStreamingProperties.getEmitterBuffer());
        return streamingEndpointSupport.stream("socks5:" + serverId, emitterTimeout,
                lineSink -> doInstallSocks5(server, landing, cred, lineSink));
    }

    @Override
    public Socks5TestRespVO testSocks5(String serverId, String echoUrl, int connectTimeoutMs, int readTimeoutMs) {
        // 校验服务器与装机子表存在
        ResourceServerDO server = resourceServerValidator.validateExists(serverId);
        Socks5InstallDO landing = resourceServerLandingValidator.validateExists(serverId);
        // IP / 端口未配置无法拨号, 转结构化失败
        if (StrUtil.isBlank(server.getIpAddress()) || ObjectUtil.isNull(landing.getSocks5Port())) {
            Socks5ProbeSnapshot failed = new Socks5ProbeSnapshot(false, 0L, echoUrl, connectTimeoutMs, readTimeoutMs,
                    0, null, "SOCKS5 IP 或端口未配置");
            return LandingSocksOpsConvert.INSTANCE.toSocks5TestVO(failed);
        }
        // 拨号探测并转换返回
        Socks5ProbeSnapshot snapshot = socks5Prober.probe(
                server.getIpAddress(), landing.getSocks5Port(),
                landing.getSocks5Username(), landing.getSocks5Password(),
                echoUrl, connectTimeoutMs, readTimeoutMs);
        return LandingSocksOpsConvert.INSTANCE.toSocks5TestVO(snapshot);
    }

    @Override
    public ServiceLogRespVO getJournalLog(String serverId, Integer lines, String level, String keyword) {
        ResourceServerDO server = resourceServerValidator.validateExists(serverId);
        resourceServerLandingValidator.validateExists(serverId);
        SessionCredential cred = buildOpsSshCred(server);
        JournalLogSnapshot snap = SshSessions.runAdHoc(cred, session ->
                serverProbe.readJournalLog(session, DANTE_UNIT, lines, level, keyword));
        return LandingSocksOpsConvert.INSTANCE.toServiceLogVO(snap);
    }

    @Override
    public ServiceLogRespVO getFileLog(String serverId, Integer lines, String keyword) {
        ResourceServerDO server = resourceServerValidator.validateExists(serverId);
        Socks5InstallDO landing = resourceServerLandingValidator.validateExists(serverId);
        SessionCredential cred = buildOpsSshCred(server);
        JournalLogSnapshot snap = SshSessions.runAdHoc(cred, session ->
                serverProbe.readFileLog(session, landing.getLogPath(), lines, keyword));
        return LandingSocksOpsConvert.INSTANCE.toServiceLogVO(snap);
    }

    /**
     * 把装机入参写回 landing 子表 (作为 dante 期望态; agent 据此装机, 服务器重置后据此重建)
     *
     * @param serverId server 编号
     * @param reqVO    装机入参
     */
    private void applyDeployConfig(String serverId, ServerLandingDeployReqVO reqVO) {
        Socks5InstallDO patch = new Socks5InstallDO();
        patch.setServerId(serverId);
        patch.setSocks5Port(reqVO.getSocks5Port());
        patch.setSocks5Username(reqVO.getSocks5Username());
        patch.setSocks5Password(reqVO.getSocks5Password());
        patch.setLogLevel(reqVO.getLogLevel());
        patch.setLogPath(reqVO.getLogPath());
        patch.setInstallDir(reqVO.getInstallDir());
        patch.setConfPath(reqVO.getConfPath());
        patch.setPamFile(reqVO.getPamFile());
        patch.setPwdFile(reqVO.getPwdFile());
        patch.setSystemdUnit(reqVO.getSystemdUnit());
        patch.setFirewallEnabled(reqVO.getFirewallEnabled());
        patch.setLogRotateEnabled(reqVO.getLogRotateEnabled());
        socks5InstallMapper.updateBySelective(patch);
    }

    /**
     * 流式装机闭包: 从期望态拼下发请求 → 经 AES 控制通道通知 agent 内置 Go 装 dante → 成功后回写
     *
     * @param server   server 主表
     * @param landing  landing 子表 (dante 期望态)
     * @param cred     SSH 凭据子表 (取 sshPort 给 agent 配 UFW 放行)
     * @param lineSink 流式输出回调
     */
    private void doInstallSocks5(ResourceServerDO server, Socks5InstallDO landing,
                                 ResourceServerCredentialDO cred, Consumer<String> lineSink) {
        // 从期望态拼装机请求 (全字段来自 socks5_install; sshPort 给 UFW 放行, timeout 给 agent)
        Socks5DeployRequest req = LandingSocksOpsConvert.INSTANCE.toDeployRequest(
                landing, cred.getSshPort(), SOCKS5_DEPLOY_TIMEOUT_SECONDS);
        lineSink.accept("[nook] === 装机 SOCKS5 / IP " + server.getIpAddress() + " ===\n");
        // 经 AES 控制通道下发, agent 内置 Go 装 dante, 流式回日志 (取代后台 SSH 推 bash); 无异常即成功
        agentControlClient.deploySocks5(server.getIpAddress(), server.getAgentToken(), req, lineSink);
        // 回写安装事实 + (仅装机中) 生命周期转待上线
        String prevState = server.getLifecycleState();
        this.finalizeInstall(server);
        boolean toReady = ResourceServerLifecycleEnum.INSTALLING.matches(prevState);
        lineSink.accept(toReady
                ? "[nook] ✔ 装机完成, 状态 装机中 → 待上线, serverId=" + server.getId() + "\n"
                : "[nook] ✔ 装机完成, 状态保持 " + prevState + " 不变, serverId=" + server.getId() + "\n");
        log.info("[doInstallSocks5] OK serverId={} ip={} prevLifecycle={} toReady={}",
                server.getId(), server.getIpAddress(), prevState, toReady);
    }

    /**
     * 装机成功后回写: installedAt + (仅装机中) 生命周期转待上线
     *
     * <p>生命周期只在原态为装机中时转待上线; 待上线 / 运行中 / 已退役 重装保留原状.
     *
     * @param server 服务器主表 DO
     */
    private void finalizeInstall(ResourceServerDO server) {
        Socks5InstallDO landingPatch = new Socks5InstallDO();
        landingPatch.setServerId(server.getId());
        landingPatch.setInstalledAt(LocalDateTime.now());
        socks5InstallMapper.updateBySelective(landingPatch);
        if (ResourceServerLifecycleEnum.INSTALLING.matches(server.getLifecycleState())) {
            resourceServerMapper.updateLifecycleState(server.getId(), ResourceServerLifecycleEnum.READY.getState());
        }
    }

    /**
     * 构造运维用 ops SSH 凭据 (含校验)
     *
     * @param server server 主表 DO
     * @return ops 路径用 SessionCredential
     */
    private SessionCredential buildOpsSshCred(ResourceServerDO server) {
        ResourceServerCredentialDO cred = resourceServerCredentialMapper.selectById(server.getId());
        resourceServerLandingValidator.validateSshCredentialReady(server, cred);
        return SessionCredential.builder()
                .serverId("ops:" + cred.getServerId())
                .sshHost(server.getIpAddress())
                .sshPort(cred.getSshPort())
                .sshUser(cred.getSshUser())
                .sshPassword(cred.getSshPassword())
                .sshTimeoutSeconds(cred.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(cred.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(cred.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(cred.getInstallTimeoutSeconds())
                .build();
    }
}
