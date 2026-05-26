package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.controller.resource.vo.HostInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5StatusRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.service.resource.ResourceServerLandingSocksOpsService;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.web.exception.BusinessException;
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
    public ResponseBodyEmitter installSocks5Stream(String serverId) {
        ResourceServerDO server = serverValidator.validateExists(serverId);
        ResourceServerLandingDO landing = landingValidator.validateExists(serverId);
        ResourceServerCredentialDO cred = credentialMapper.selectById(serverId);
        validateInstallReady(server, landing, cred);

        Duration emitterTimeout = Duration.ofSeconds(cred.getInstallTimeoutSeconds())
                .plus(webStreamingProperties.getEmitterBuffer());
        return streamingSupport.stream("socks5:" + serverId, emitterTimeout,
                lineSink -> doInstallSocks5(server, landing, cred, lineSink));
    }

    private void doInstallSocks5(ResourceServerDO server, ResourceServerLandingDO landing,
                                 ResourceServerCredentialDO cred, Consumer<String> lineSink) {
        SessionCredential sshCred = buildInstallSshCred(server, cred);
        Map<String, String> vars = buildInstallVars(landing, cred);
        Duration timeout = Duration.ofSeconds(cred.getInstallTimeoutSeconds());
        lineSink.accept("[nook] === 装机 SOCKS5 / IP " + server.getIpAddress() + " ===\n");
        SshSessions.runAdHocVoid(sshCred, session ->
                scriptCatalog.run(session, NookScripts.SOCKS5_INSTALL, vars, timeout, lineSink));

        // 装机成功 → installedAt + 主表 lifecycle → LIVE
        LocalDateTime now = LocalDateTime.now();
        ResourceServerLandingDO landingPatch = new ResourceServerLandingDO();
        landingPatch.setServerId(server.getId());
        landingPatch.setInstalledAt(now);
        landingMapper.updateBySelective(landingPatch);
        if (!ResourceServerLifecycleEnum.LIVE.name().equals(server.getLifecycleState())) {
            serverMapper.updateLifecycleState(server.getId(), ResourceServerLifecycleEnum.LIVE.name());
        }
        lineSink.accept("[nook] ✔ 装机完成, lifecycle → LIVE, serverId=" + server.getId() + "\n");
        log.info("[install-socks5] OK serverId={} ip={} lifecycle=LIVE",
                server.getId(), server.getIpAddress());
    }

    private void validateInstallReady(ResourceServerDO server, ResourceServerLandingDO landing,
                                      ResourceServerCredentialDO cred) {
        if (cred == null || StrUtil.isBlank(server.getIpAddress()) || StrUtil.isBlank(cred.getSshPassword())) {
            throw new BusinessException(ResourceErrorCode.LANDING_SSH_CRED_MISSING, server.getIpAddress());
        }
        if (landing.getSocks5Port() == null
                || StrUtil.isBlank(landing.getSocks5Username()) || StrUtil.isBlank(landing.getSocks5Password())) {
            throw new BusinessException(ResourceErrorCode.LANDING_SOCKS5_INCOMPLETE, server.getIpAddress());
        }
    }

    private SessionCredential buildInstallSshCred(ResourceServerDO server, ResourceServerCredentialDO cred) {
        return SessionCredential.builder()
                .serverId("install:" + cred.getServerId())
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
    public Socks5StatusRespVO getStatus(String serverId) {
        ResourceServerDO server = serverValidator.validateExists(serverId);
        ResourceServerLandingDO landing = landingValidator.validateExists(serverId);
        SessionCredential cred = buildOpsSshCred(server);
        return SshSessions.runAdHoc(cred, session -> {
            SystemdStatusSnapshot sysd = serverProbe.readSystemdStatus(session, DANTE_UNIT);
            String version = readDanteVersion(session);
            String listening = readDanteListening(session, landing.getSocks5Port());
            String ufw = serverProbe.readUfwStatus(session);
            HostInfoSnapshot host = serverProbe.readHostInfo(session);

            Socks5StatusRespVO vo = new Socks5StatusRespVO();
            vo.setUnit(sysd.getUnit());
            vo.setActive(sysd.getActive());
            vo.setUptimeFrom(sysd.getUptimeFrom());
            vo.setEnabled(sysd.getEnabled());
            vo.setVersion(version);
            vo.setListening(listening);
            vo.setUfwStatus(ufw);
            vo.setHostInfo(toHostInfoVO(host));
            return vo;
        });
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
        log.info("[landing] SOCKS5 autostart serverId={} ip={} enabled={}", serverId, server.getIpAddress(), enabled);
    }

    @Override
    public ServiceLogRespVO getJournalLog(String serverId, Integer lines, String level, String keyword) {
        ResourceServerDO server = serverValidator.validateExists(serverId);
        landingValidator.validateExists(serverId);
        SessionCredential cred = buildOpsSshCred(server);
        JournalLogSnapshot snap = SshSessions.runAdHoc(cred, session ->
                serverProbe.readJournalLog(session, DANTE_UNIT, lines, level, keyword));
        return toServiceLogVO(snap);
    }

    @Override
    public ServiceLogRespVO getFileLog(String serverId, Integer lines, String keyword) {
        ResourceServerDO server = serverValidator.validateExists(serverId);
        ResourceServerLandingDO landing = landingValidator.validateExists(serverId);
        SessionCredential cred = buildOpsSshCred(server);
        JournalLogSnapshot snap = SshSessions.runAdHoc(cred, session ->
                serverProbe.readFileLog(session, landing.getLogPath(), lines, keyword));
        return toServiceLogVO(snap);
    }

    private SessionCredential buildOpsSshCred(ResourceServerDO server) {
        ResourceServerCredentialDO cred = credentialMapper.selectById(server.getId());
        if (cred == null || StrUtil.isBlank(cred.getSshPassword())) {
            throw new BusinessException(ResourceErrorCode.LANDING_SSH_CRED_MISSING, server.getIpAddress());
        }
        return SessionCredential.builder()
                .serverId("ops:" + server.getId())
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

    /** dpkg-query 拿 dante 包版本. */
    private String readDanteVersion(SshSession session) {
        String out = session.ssh().exec(
                "dpkg-query -W -f='${Version}' dante-server 2>/dev/null || true").getStdout();
        return out == null ? "" : out.trim();
    }

    /** ss -ltn 过滤 socks5 端口监听行. */
    private String readDanteListening(SshSession session, Integer socksPort) {
        if (socksPort == null) return "";
        String cmd = "ss -ltn 2>/dev/null | awk 'NR==1 || $4 ~ /:" + socksPort + "$/' || true";
        String out = session.ssh().exec(cmd).getStdout();
        return out == null ? "" : out.trim();
    }

    private static HostInfoRespVO toHostInfoVO(HostInfoSnapshot s) {
        if (s == null) return null;
        HostInfoRespVO vo = new HostInfoRespVO();
        vo.setHostname(s.getHostname());
        vo.setKernel(s.getKernel());
        vo.setOsRelease(s.getOsRelease());
        vo.setSystemUptime(s.getSystemUptime());
        vo.setLoadAvg(s.getLoadAvg());
        vo.setMemory(s.getMemory());
        vo.setDisk(s.getDisk());
        vo.setTimezone(s.getTimezone());
        return vo;
    }

    private static ServiceLogRespVO toServiceLogVO(JournalLogSnapshot snap) {
        ServiceLogRespVO vo = new ServiceLogRespVO();
        vo.setUnit(snap.getUnit());
        vo.setLines(snap.getLines());
        vo.setLevel(snap.getLevel());
        vo.setKeyword(snap.getKeyword());
        vo.setLog(snap.getLog());
        return vo;
    }
}
