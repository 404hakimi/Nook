package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingDeployReqVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.convert.resource.LandingSocksOpsConvert;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.mapper.Socks5InstallMapper;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.service.resource.ResourceServerLandingSocksOpsService;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.date.DateUtils;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.web.StreamingEndpointSupport;
import com.nook.framework.web.WebStreamingProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

/**
 * SOCKS5 落地节点 SSH 运维 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerLandingSocksOpsServiceImpl implements ResourceServerLandingSocksOpsService {

    /** dante 的 systemd unit 名 (apt 包默认). */
    private static final String DANTE_UNIT = "danted";

    /** ServerProbe 返回的 uptime 格式 (date -d 重格式化为 ISO-like + 数字时区). */
    private static final DateTimeFormatter UPTIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

    @Resource
    private ScriptCatalog scriptCatalog;
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
        ResourceServerDO server = resourceServerValidator.validateExists(serverId);
        resourceServerLandingValidator.validateExists(serverId);
        ResourceServerCredentialDO cred = resourceServerCredentialMapper.selectById(serverId);
        resourceServerLandingValidator.validateSshCredentialReady(server, cred);

        // 装机配置写回 landing 子表; 重读 DO 保证 buildInstallVars 拿到最新值
        this.applyDeployConfig(serverId, reqVO);
        Socks5InstallDO landing = socks5InstallMapper.selectByServerId(serverId);
        resourceServerLandingValidator.validateSocks5ConfigReady(landing);

        Duration emitterTimeout = Duration.ofSeconds(cred.getInstallTimeoutSeconds())
                .plus(webStreamingProperties.getEmitterBuffer());
        return streamingEndpointSupport.stream("socks5:" + serverId, emitterTimeout,
                lineSink -> doInstallSocks5(server, landing, cred, lineSink));
    }

    @Override
    public Socks5ProbeSnapshot testSocks5(String serverId, String echoUrl, int connectTimeoutMs, int readTimeoutMs) {
        ResourceServerDO server = resourceServerValidator.validateExists(serverId);
        Socks5InstallDO landing = resourceServerLandingValidator.validateExists(serverId);
        if (StrUtil.isBlank(server.getIpAddress()) || ObjectUtil.isNull(landing.getSocks5Port())) {
            return new Socks5ProbeSnapshot(false, 0L, echoUrl, connectTimeoutMs, readTimeoutMs,
                    0, null, "SOCKS5 IP 或端口未配置");
        }
        return socks5Prober.probe(
                server.getIpAddress(), landing.getSocks5Port(),
                landing.getSocks5Username(), landing.getSocks5Password(),
                echoUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public void setAutostart(String serverId, boolean enabled) {
        ResourceServerDO server = resourceServerValidator.validateExists(serverId);
        resourceServerLandingValidator.validateExists(serverId);
        SessionCredential cred = buildOpsSshCred(server);
        SshSessions.runAdHocVoid(cred, session -> {
            String cmd = (enabled ? "systemctl enable " : "systemctl disable ") + DANTE_UNIT + " 2>&1 || true";
            session.ssh().exec(cmd);
        });

        Socks5InstallDO patch = new Socks5InstallDO();
        patch.setServerId(serverId);
        patch.setAutostartEnabled(enabled ? 1 : 0);
        socks5InstallMapper.updateBySelective(patch);
        log.info("[setAutostart] serverId={} ip={} enabled={}", serverId, server.getIpAddress(), enabled);
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
     * 把装机入参写回 landing 子表 (装机脚本会基于这些字段渲染远端配置)
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
        patch.setAutostartEnabled(reqVO.getAutostartEnabled());
        patch.setFirewallEnabled(reqVO.getFirewallEnabled());
        patch.setLogRotateEnabled(reqVO.getLogRotateEnabled());
        socks5InstallMapper.updateBySelective(patch);
    }

    /**
     * 流式装机闭包: 同一 SSH session 内跑装机脚本 + 探包版本/进程启动时间, 装机成功后回写 DB
     *
     * @param server   server 主表
     * @param landing  landing 子表 (含装机所需配置)
     * @param cred     SSH 凭据子表
     * @param lineSink 流式输出回调
     */
    private void doInstallSocks5(ResourceServerDO server, Socks5InstallDO landing,
                                 ResourceServerCredentialDO cred, Consumer<String> lineSink) {
        SessionCredential sshCred = buildSshCred(server, cred, "install");
        Map<String, String> vars = buildInstallVars(landing, cred);
        Duration timeout = Duration.ofSeconds(cred.getInstallTimeoutSeconds());
        lineSink.accept("[nook] === 装机 SOCKS5 / IP " + server.getIpAddress() + " ===\n");

        PostInstallFacts facts = SshSessions.runAdHoc(sshCred, session -> {
            scriptCatalog.run(session, NookScripts.SOCKS5_INSTALL, vars, timeout, lineSink);
            String ver = readDanteVersion(session);
            SystemdStatusSnapshot sysd = serverProbe.readSystemdStatus(session, DANTE_UNIT);
            return new PostInstallFacts(ver, sysd.getUptimeFrom());
        });

        String prevState = server.getLifecycleState();
        this.finalizeInstall(server, facts);
        boolean toLive = ResourceServerLifecycleEnum.INSTALLING.matches(prevState);
        lineSink.accept(toLive
                ? "[nook] ✔ 装机完成, lifecycle 装机中 → 运行中, serverId=" + server.getId() + "\n"
                : "[nook] ✔ 装机完成, lifecycle 保持 " + prevState + " 不变, serverId=" + server.getId() + "\n");
        log.info("[doInstallSocks5] OK serverId={} ip={} prevLifecycle={} toLive={} version={}",
                server.getId(), server.getIpAddress(), prevState, toLive, facts.version());
    }

    /**
     * 装机成功后回写: 落地节点安装信息 + (仅装机中) 生命周期转运行中
     *
     * <p>lifecycle 只在原态为装机中时转 LIVE; READY/LIVE/RETIRED 重装保留原状.
     * <p>不加事务: 两条更新是同一事件的两个独立事实记录, 无原子依赖.
     * <p>极端场景 (安装信息写成功但生命周期切换失败) 重试装机即可 (同版本幂等, 不会真重装).
     *
     * @param server 服务器主表 DO
     * @param facts  装机后远端探测事实
     */
    private void finalizeInstall(ResourceServerDO server, PostInstallFacts facts) {
        LocalDateTime now = LocalDateTime.now();
        Socks5InstallDO landingPatch = new Socks5InstallDO();
        landingPatch.setServerId(server.getId());
        landingPatch.setInstalledAt(now);
        landingPatch.setDanteVersion(facts.version());
        landingPatch.setLastDanteUptime(DateUtils.parseOffsetOrFallback(facts.uptimeRaw(), UPTIME_FORMATTER, now));
        socks5InstallMapper.updateBySelective(landingPatch);
        // 仅装机中 → 运行中; READY/LIVE/RETIRED 重装时保留原 lifecycle 不动 (运维重装不改变已定生命周期)
        if (ResourceServerLifecycleEnum.INSTALLING.matches(server.getLifecycleState())) {
            resourceServerMapper.updateLifecycleState(server.getId(), ResourceServerLifecycleEnum.LIVE.getState());
        }
    }

    /**
     * 装机成功后从远端探测到的 dante 事实, 用于回写 DB
     *
     * @author nook
     */
    private record PostInstallFacts(String version, String uptimeRaw) { }

    /**
     * 构造装机用 ops SSH 凭据 (含校验)
     *
     * @param server server 主表 DO
     * @return ops 路径用 SessionCredential
     */
    private SessionCredential buildOpsSshCred(ResourceServerDO server) {
        ResourceServerCredentialDO cred = resourceServerCredentialMapper.selectById(server.getId());
        resourceServerLandingValidator.validateSshCredentialReady(server, cred);
        return this.buildSshCred(server, cred, "ops");
    }

    /**
     * 把 server + credential + 用途 prefix 装配成 SshSessions 用的 SessionCredential
     *
     * @param server server 主表
     * @param cred   credential 子表
     * @param prefix session 标识前缀 (install / ops, 便于日志排查)
     * @return SessionCredential
     */
    private SessionCredential buildSshCred(ResourceServerDO server, ResourceServerCredentialDO cred, String prefix) {
        return SessionCredential.builder()
                .serverId(prefix + ":" + cred.getServerId())
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

    /**
     * 装机脚本需要的 ENV 变量
     *
     * @param l    landing 子表 (含 socks5 凭据 / install 路径 / 开关)
     * @param cred credential 子表 (含 sshPort 给脚本渲染日志)
     * @return 不可变 ENV map
     */
    private Map<String, String> buildInstallVars(Socks5InstallDO l, ResourceServerCredentialDO cred) {
        return Map.ofEntries(
                Map.entry("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.entry("SOCKS_PORT", String.valueOf(l.getSocks5Port())),
                Map.entry("SSH_PORT", String.valueOf(cred.getSshPort())),
                Map.entry("SOCKS_USER", l.getSocks5Username()),
                Map.entry("SOCKS_PASS", l.getSocks5Password()),
                Map.entry("FIREWALL_ENABLED", String.valueOf(l.getFirewallEnabled())),
                Map.entry("LOG_LEVEL", l.getLogLevel()),
                Map.entry("LOG_PATH", l.getLogPath()),
                Map.entry("INSTALL_DIR", l.getInstallDir()),
                Map.entry("CONF_PATH", l.getConfPath()),
                Map.entry("PAM_FILE", l.getPamFile()),
                Map.entry("PWD_FILE", l.getPwdFile()),
                Map.entry("SYSTEMD_UNIT", DANTE_UNIT),
                Map.entry("AUTOSTART_ENABLED", String.valueOf(l.getAutostartEnabled())),
                Map.entry("LOG_ROTATE_ENABLED", String.valueOf(l.getLogRotateEnabled())));
    }

    /** 查询 dante 安装包版本. */
    private String readDanteVersion(SshSession session) {
        String out = session.ssh().exec(
                "dpkg-query -W -f='${Version}' dante-server 2>/dev/null || true").getStdout();
        return ObjectUtil.isNull(out) ? "" : out.trim();
    }

}
