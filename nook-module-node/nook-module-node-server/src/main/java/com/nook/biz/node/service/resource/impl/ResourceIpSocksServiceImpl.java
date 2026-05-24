package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.resource.vo.HostInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5StatusRespVO;
import com.nook.biz.node.convert.socks5.Socks5OpsConvert;
import com.nook.biz.node.api.enums.ResourceIpPoolLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolStatusEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolInstallDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolRuntimeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolSocks5DO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolBillingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolCredentialMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolInstallMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolRuntimeMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolSocks5Mapper;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.service.resource.ResourceIpSocksService;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 资源 IP SOCKS5 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceIpSocksServiceImpl implements ResourceIpSocksService {

    private final ScriptCatalog scriptCatalog;
    private final Socks5Prober socks5Prober;
    private final ResourceIpPoolValidator ipPoolValidator;
    private final ServerProbe serverProbe;
    private final ResourceIpPoolMapper ipPoolMapper;
    private final ResourceIpPoolCredentialMapper credentialMapper;
    private final ResourceIpPoolBillingMapper billingMapper;
    private final ResourceIpPoolSocks5Mapper socks5Mapper;
    private final ResourceIpPoolInstallMapper installMapper;
    private final ResourceIpPoolRuntimeMapper runtimeMapper;
    private final TransactionTemplate transactionTemplate;

    /** dante 的 systemd unit 名 (apt 包默认), 跟 install / update 脚本里 systemctl 操作的 unit 对齐. */
    private static final String DANTE_UNIT = "danted";

    @Override
    public void installSocks5(String ipId, Consumer<String> lineSink) {
        // 1. 拉 IP 池 + 5 子表 (createIpPool 已落好), 校验配置齐全
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        ResourceIpPoolCredentialDO cred = credentialMapper.selectById(ipId);
        ResourceIpPoolSocks5DO socks5 = socks5Mapper.selectById(ipId);
        ResourceIpPoolInstallDO install = installMapper.selectById(ipId);
        if (cred == null || StrUtil.isBlank(cred.getSshHost()) || StrUtil.isBlank(cred.getSshPassword())) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_SOCKS5_INCOMPLETE,
                    "ipId=" + ipId + " 的 SSH 凭据未填全, 先在编辑 SSH 凭据里补齐");
        }
        if (socks5 == null || socks5.getSocks5Port() == null
                || StrUtil.isBlank(socks5.getSocks5Username()) || StrUtil.isBlank(socks5.getSocks5Password())) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_SOCKS5_INCOMPLETE,
                    "ipId=" + ipId + " 的 SOCKS5 业务配置未填全, 先在编辑 SOCKS5 配置里补齐");
        }
        if (install == null) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_SOCKS5_INCOMPLETE,
                    "ipId=" + ipId + " 的装机产物子表缺失");
        }

        // 2. SSH 跑装机脚本 (副作用不可回滚, 放事务外); 装机脚本耗时 1-5min, 给 10min 兜底
        SessionCredential sshCred = buildSshCred(cred, install);
        Map<String, String> vars = buildInstallVars(ip, cred, socks5, install);
        Duration installTimeout = Duration.ofSeconds(600);
        lineSink.accept("[nook] === 装机 SOCKS5 / IP " + ip.getIpAddress() + " ===\n");
        SshSessions.runAdHocVoid(sshCred, session ->
                scriptCatalog.run(session, NookScripts.SOCKS5_INSTALL, vars, installTimeout, lineSink));

        // 3. 装机成功 → update install 表 (installedAt) + 主表 lifecycle 切到 LIVE
        LocalDateTime now = LocalDateTime.now();
        ResourceIpPoolInstallDO installPatch = new ResourceIpPoolInstallDO();
        installPatch.setIpId(ipId);
        installPatch.setInstalledAt(now);
        installMapper.updateById(installPatch);
        if (!ResourceIpPoolLifecycleEnum.LIVE.getState().equals(ip.getLifecycleState())) {
            ipPoolMapper.updateLifecycleState(ipId, ResourceIpPoolLifecycleEnum.LIVE.getState());
        }
        // agent_token 若未生成, 这时补 (装好后让 landing agent 可以接管 push)
        if (StrUtil.isBlank(ip.getAgentToken())) {
            ResourceIpPoolDO mainPatch = new ResourceIpPoolDO();
            mainPatch.setId(ipId);
            mainPatch.setAgentToken(generateAgentToken());
            ipPoolMapper.updateById(mainPatch);
        }
        lineSink.accept("[nook] ✔ 装机完成, lifecycle → LIVE, ipId=" + ipId + "\n");
        log.info("[install-socks5] OK ipId={} ip={} lifecycle=LIVE", ipId, ip.getIpAddress());
    }

    /** SSH 凭据从 credential / install 子表读 (装机脚本要的超时 / 端口 / 用户). */
    private SessionCredential buildSshCred(ResourceIpPoolCredentialDO cred, ResourceIpPoolInstallDO install) {
        return SessionCredential.builder()
                .serverId("ad-hoc:" + cred.getSshHost())
                .sshHost(cred.getSshHost())
                .sshPort(cred.getSshPort() == null ? 22 : cred.getSshPort())
                .sshUser(StrUtil.blankToDefault(cred.getSshUser(), "root"))
                .sshPassword(cred.getSshPassword())
                .sshTimeoutSeconds(60)
                .sshOpTimeoutSeconds(60)
                .sshUploadTimeoutSeconds(180)
                .installTimeoutSeconds(600)
                .build();
    }

    /** 装机模板变量: 全部从 DB 已落库的子表读. */
    private Map<String, String> buildInstallVars(ResourceIpPoolDO ip, ResourceIpPoolCredentialDO cred,
                                                  ResourceIpPoolSocks5DO socks5, ResourceIpPoolInstallDO install) {
        return Map.ofEntries(
                Map.entry("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.entry("SOCKS_PORT", String.valueOf(socks5.getSocks5Port())),
                Map.entry("SSH_PORT", String.valueOf(cred.getSshPort() == null ? 22 : cred.getSshPort())),
                Map.entry("SOCKS_USER", socks5.getSocks5Username()),
                Map.entry("SOCKS_PASS", socks5.getSocks5Password()),
                Map.entry("FIREWALL_ENABLED", String.valueOf(install.getFirewallEnabled() == null ? 1 : install.getFirewallEnabled())),
                Map.entry("LOG_LEVEL", socks5.getLogLevel()),
                Map.entry("LOG_PATH", install.getLogPath()),
                Map.entry("INSTALL_DIR", install.getInstallDir()),
                Map.entry("CONF_PATH", install.getConfPath()),
                Map.entry("PAM_FILE", install.getPamFile()),
                Map.entry("PWD_FILE", install.getPwdFile()),
                Map.entry("SYSTEMD_UNIT", DANTE_UNIT),
                Map.entry("AUTOSTART_ENABLED", String.valueOf(install.getAutostartEnabled() == null ? 1 : install.getAutostartEnabled())),
                Map.entry("LOG_ROTATE_ENABLED", String.valueOf(install.getLogRotateEnabled() == null ? 1 : install.getLogRotateEnabled())));
    }

    /** SHA256(UUID + UUID) → 64 char hex; 跟 DB agent_token CHAR(64) 长度对齐 */
    private static String generateAgentToken() {
        String raw = UUID.randomUUID() + UUID.randomUUID().toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes()));
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "SHA-256 不可用: " + e.getMessage());
        }
    }

    @Override
    public ResourceIpSocksTestRespVO testSocks5(String ipId, ResourceIpSocksTestReqVO reqVO) {
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        ResourceIpPoolSocks5DO socks5 = socks5Mapper.selectById(ipId);
        if (StrUtil.isBlank(ip.getIpAddress()) || socks5 == null || ObjectUtil.isNull(socks5.getSocks5Port())) {
            // 凭据未配置时不调 prober, 直接返回结构化失败; echoUrl / 超时回填原值便于前端控制台展示
            ResourceIpSocksTestRespVO vo = new ResourceIpSocksTestRespVO();
            vo.setSuccess(false);
            vo.setEchoUrl(reqVO.getEchoUrl());
            vo.setConnectTimeoutMs(reqVO.getConnectTimeoutMs());
            vo.setReadTimeoutMs(reqVO.getReadTimeoutMs());
            vo.setError("SOCKS5 IP 或端口未配置");
            return vo;
        }
        Socks5ProbeSnapshot snap = socks5Prober.probe(
                ip.getIpAddress(), socks5.getSocks5Port(), socks5.getSocks5Username(), socks5.getSocks5Password(),
                reqVO.getEchoUrl(), reqVO.getConnectTimeoutMs(), reqVO.getReadTimeoutMs());
        return Socks5OpsConvert.INSTANCE.convert(snap);
    }

    // ===== status / autostart / log: 走 IP 池 credential 子表存储的 SSH 凭据 =====

    @Override
    public Socks5StatusRespVO getSocks5Status(String ipId) {
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        ResourceIpPoolSocks5DO socks5 = socks5Mapper.selectById(ipId);
        SessionCredential cred = buildStoredCred(ip);
        return SshSessions.runAdHoc(cred, session -> {
            SystemdStatusSnapshot sysd = serverProbe.readSystemdStatus(session, DANTE_UNIT);
            String version = readDanteVersion(session);
            String listening = readDanteListening(session, socks5 == null ? null : socks5.getSocks5Port());
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

    @Override
    public void setSocks5Autostart(String ipId, boolean enabled) {
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        SessionCredential cred = buildStoredCred(ip);
        SshSessions.runAdHocVoid(cred, session -> {
            String cmd = (enabled ? "systemctl enable " : "systemctl disable ") + DANTE_UNIT
                    + " 2>&1 || true";
            session.ssh().exec(cmd);
        });

        // 同步 install 子表的 autostart_enabled, 让列表 / 编辑表单展示跟远端实际一致
        ResourceIpPoolInstallDO patch = new ResourceIpPoolInstallDO();
        patch.setIpId(ipId);
        patch.setAutostartEnabled(enabled ? 1 : 0);
        installMapper.updateById(patch);
        log.info("[ip-pool] SOCKS5 autostart ipId={} ip={} enabled={}", ipId, ip.getIpAddress(), enabled);
    }

    @Override
    public ServiceLogRespVO getSocks5Log(String ipId, Integer lines, String level, String keyword) {
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        SessionCredential cred = buildStoredCred(ip);
        JournalLogSnapshot snap = SshSessions.runAdHoc(cred, session ->
                serverProbe.readJournalLog(session, DANTE_UNIT, lines, level, keyword));

        return toServiceLogVO(snap);
    }

    @Override
    public ServiceLogRespVO getSocks5LogFile(String ipId, Integer lines, String keyword) {
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        ResourceIpPoolInstallDO install = installMapper.selectById(ipId);
        String filePath = install == null ? null : install.getLogPath();
        SessionCredential cred = buildStoredCred(ip);
        JournalLogSnapshot snap = SshSessions.runAdHoc(cred, session ->
                serverProbe.readFileLog(session, filePath, lines, keyword));
        return toServiceLogVO(snap);
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

    /**
     * 从 IP 池 credential 子表取存储的 SSH 凭据组装 SessionCredential.
     * 缺省策略: sshHost 留空 → 用 ipAddress; sshPort 留空 → 22; sshUser 留空 → root.
     * 密码必须有值, 否则抛业务错让用户回编辑表单补.
     */
    private SessionCredential buildStoredCred(ResourceIpPoolDO ip) {
        ResourceIpPoolCredentialDO cred = credentialMapper.selectById(ip.getId());
        if (cred == null || StrUtil.isBlank(cred.getSshPassword())) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_SSH_CRED_MISSING, ip.getIpAddress());
        }
        String host = StrUtil.blankToDefault(cred.getSshHost(), ip.getIpAddress());
        int port = ObjectUtil.defaultIfNull(cred.getSshPort(), 22);
        String user = StrUtil.blankToDefault(cred.getSshUser(), "root");
        return SessionCredential.builder()
                .serverId("ip-pool:" + ip.getId())
                .sshHost(host)
                .sshPort(port)
                .sshUser(user)
                .sshPassword(cred.getSshPassword())
                .sshTimeoutSeconds(30)
                .sshOpTimeoutSeconds(30)
                .sshUploadTimeoutSeconds(60)
                .installTimeoutSeconds(120)
                .build();
    }

    /** dpkg-query 拿 dante 包版本; 失败兜底空串, 让前端展示 '-' 即可. */
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

    private static final String DEFAULT_INSTALL_DIR = "/home/socks5";
    private static final String DEFAULT_LOG_LEVEL = "connect disconnect error";
}
