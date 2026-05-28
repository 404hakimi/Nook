package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.controller.resource.vo.ServerLandingDeployReqVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.convert.resource.LandingSocksOpsConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
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
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ResourceServerLandingSocksOpsServiceImpl implements ResourceServerLandingSocksOpsService {

    /** dante 的 systemd unit 名 (apt 包默认). */
    private static final String DANTE_UNIT = "danted";

    /** ServerProbe 返回的 uptime 格式 (date -d 重格式化为 ISO-like + 数字时区). */
    private static final DateTimeFormatter UPTIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

    private final ScriptCatalog scriptCatalog;
    private final Socks5Prober socks5Prober;
    private final ServerProbe serverProbe;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerLandingValidator landingValidator;
    private final ResourceServerMapper serverMapper;
    private final ResourceServerLandingMapper landingMapper;
    private final ResourceServerCredentialMapper credentialMapper;
    private final StreamingEndpointSupport streamingSupport;
    private final WebStreamingProperties webStreamingProperties;

    @Override
    public ResponseBodyEmitter installSocks5Stream(String serverId, ServerLandingDeployReqVO reqVO) {
        ResourceServerDO server = serverValidator.validateExists(serverId);
        landingValidator.validateExists(serverId);
        ResourceServerCredentialDO cred = credentialMapper.selectById(serverId);
        landingValidator.validateSshCredentialReady(server, cred);

        // 装机配置写回 landing 子表; 重读 DO 保证 buildInstallVars 拿到最新值
        applyDeployConfig(serverId, reqVO);
        ResourceServerLandingDO landing = landingMapper.selectByServerId(serverId);
        landingValidator.validateSocks5ConfigReady(landing);

        Duration emitterTimeout = Duration.ofSeconds(cred.getInstallTimeoutSeconds())
                .plus(webStreamingProperties.getEmitterBuffer());
        return streamingSupport.stream("socks5:" + serverId, emitterTimeout,
                lineSink -> doInstallSocks5(server, landing, cred, lineSink));
    }

    @Override
    public Socks5ProbeSnapshot testSocks5(String serverId, String echoUrl, int connectTimeoutMs, int readTimeoutMs) {
        ResourceServerDO server = serverValidator.validateExists(serverId);
        ResourceServerLandingDO landing = landingValidator.validateExists(serverId);
        if (StrUtil.isBlank(server.getIpAddress()) || landing.getSocks5Port() == null) {
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
        ResourceServerDO server = serverValidator.validateExists(serverId);
        landingValidator.validateExists(serverId);
        SessionCredential cred = buildOpsSshCred(server);
        SshSessions.runAdHocVoid(cred, session -> {
            String cmd = (enabled ? "systemctl enable " : "systemctl disable ") + DANTE_UNIT + " 2>&1 || true";
            session.ssh().exec(cmd);
        });

        ResourceServerLandingDO patch = new ResourceServerLandingDO();
        patch.setServerId(serverId);
        patch.setAutostartEnabled(enabled ? 1 : 0);
        landingMapper.updateBySelective(patch);
        log.info("[setAutostart] serverId={} ip={} enabled={}", serverId, server.getIpAddress(), enabled);
    }

    @Override
    public ServiceLogRespVO getJournalLog(String serverId, Integer lines, String level, String keyword) {
        ResourceServerDO server = serverValidator.validateExists(serverId);
        landingValidator.validateExists(serverId);
        SessionCredential cred = buildOpsSshCred(server);
        JournalLogSnapshot snap = SshSessions.runAdHoc(cred, session ->
                serverProbe.readJournalLog(session, DANTE_UNIT, lines, level, keyword));
        return LandingSocksOpsConvert.INSTANCE.toServiceLogVO(snap);
    }

    @Override
    public ServiceLogRespVO getFileLog(String serverId, Integer lines, String keyword) {
        ResourceServerDO server = serverValidator.validateExists(serverId);
        ResourceServerLandingDO landing = landingValidator.validateExists(serverId);
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
        ResourceServerLandingDO patch = new ResourceServerLandingDO();
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
        landingMapper.updateBySelective(patch);
    }

    /**
     * 流式装机闭包: 同一 SSH session 内跑装机脚本 + 探包版本/进程启动时间, 装机成功后回写 DB
     *
     * @param server   server 主表
     * @param landing  landing 子表 (含装机所需配置)
     * @param cred     SSH 凭据子表
     * @param lineSink 流式输出回调
     */
    private void doInstallSocks5(ResourceServerDO server, ResourceServerLandingDO landing,
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

        finalizeInstall(server, facts);
        lineSink.accept("[nook] ✔ 装机完成, lifecycle → LIVE, serverId=" + server.getId() + "\n");
        log.info("[doInstallSocks5] OK serverId={} ip={} lifecycle=LIVE version={}",
                server.getId(), server.getIpAddress(), facts.version());
    }

    /**
     * 装机成功后的 DB 回写: landing 子表 (installedAt/danteVersion/lastDanteUptime) + 主表 lifecycle → LIVE.
     *
     * <p>不加事务: 两条 update 是同一事件的两个独立事实记录, 无原子依赖.
     * <p>极端场景 (landing 写成功但 lifecycle 切失败) 重试装机即可 (dpkg 同版本不会真重装, 幂等).
     *
     * @param server server 主表 DO
     * @param facts  装机后远端探测事实
     */
    private void finalizeInstall(ResourceServerDO server, PostInstallFacts facts) {
        LocalDateTime now = LocalDateTime.now();
        ResourceServerLandingDO landingPatch = new ResourceServerLandingDO();
        landingPatch.setServerId(server.getId());
        landingPatch.setInstalledAt(now);
        landingPatch.setDanteVersion(facts.version());
        landingPatch.setLastDanteUptime(DateUtils.parseOffsetOrFallback(facts.uptimeRaw(), UPTIME_FORMATTER, now));
        landingMapper.updateBySelective(landingPatch);
        if (!ResourceServerLifecycleEnum.LIVE.matches(server.getLifecycleState())) {
            serverMapper.updateLifecycleState(server.getId(), ResourceServerLifecycleEnum.LIVE.getState());
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
        ResourceServerCredentialDO cred = credentialMapper.selectById(server.getId());
        landingValidator.validateSshCredentialReady(server, cred);
        return buildSshCred(server, cred, "ops");
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
    private Map<String, String> buildInstallVars(ResourceServerLandingDO l, ResourceServerCredentialDO cred) {
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

    /** dpkg-query 拿 dante 包版本. */
    private String readDanteVersion(SshSession session) {
        String out = session.ssh().exec(
                "dpkg-query -W -f='${Version}' dante-server 2>/dev/null || true").getStdout();
        return out == null ? "" : out.trim();
    }

}
