package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.resource.vo.HostInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksInstallReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksSyncCredsReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5StatusRespVO;
import com.nook.biz.node.convert.socks5.Socks5OpsConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
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
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.framework.xray.cli.XrayOutboundCli;
import com.nook.biz.node.service.resource.ResourceIpSocksService;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.biz.node.validator.XrayServerValidator;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
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
    private final XrayClientMapper xrayClientMapper;
    private final XrayServerValidator xrayServerValidator;
    private final XrayOutboundCli outboundCli;
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
    public void installSocks5(ResourceIpSocksInstallReqVO reqVO, Consumer<String> lineSink) {
        // 1. 装机前查重: 同 IP 已在池里 → 拒绝, 防 admin 反复装机导致脏数据
        ipPoolValidator.validateIpAddressUnique(null, reqVO.getSshHost());

        // 2. SSH 跑装机脚本 (副作用不可回滚, 放事务外)
        SessionCredential cred = buildAdHocCred(reqVO);
        Map<String, String> vars = buildVars(reqVO);
        Duration installTimeout = Duration.ofSeconds(reqVO.getInstallTimeoutSeconds());
        SshSessions.runAdHocVoid(cred, session ->
                scriptCatalog.run(session, NookScripts.SOCKS5_INSTALL, vars, installTimeout, lineSink));

        // 3. 装机成功 → 事务内一次性落 6 行 (主表 + 5 子表); 失败抛错, 远端服务已起来但 admin 可手动清理
        String ipId = persistFromInstall(reqVO);

        // 4. 把 ipId 通过 lineSink 透回前端, 客户端解析这一行拿 id 跳转详情页
        lineSink.accept("[nook] ✔ 已落库 ipId=" + ipId + "\n");
        log.info("[install-socks5] OK ipId={} ip={} provisionMode=SELF_DEPLOY", ipId, reqVO.getSshHost());
    }

    /** 装机成功后事务内一次性落 6 行 (主表 + 5 子表); 跟 ResourceIpPoolServiceImpl.createIpPool 同结构 */
    private String persistFromInstall(ResourceIpSocksInstallReqVO r) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            // 主表
            ResourceIpPoolDO main = new ResourceIpPoolDO();
            main.setRegion(r.getRegion());
            main.setIpTypeId(r.getIpTypeId());
            main.setIpAddress(r.getSshHost());
            main.setLifecycleState(ResourceIpPoolLifecycleEnum.LIVE.getState());
            main.setStatus(ResourceIpPoolStatusEnum.AVAILABLE.getState());
            main.setProvisionMode(ResourceIpPoolProvisionModeEnum.SELF_DEPLOY.getMode());
            main.setAgentToken(generateAgentToken());
            main.setRemark(r.getRemark());
            ipPoolMapper.insert(main);
            String ipId = main.getId();

            // credential 子表 (SSH 凭据从入参落库, 后续运维 status / 日志接口要用)
            ResourceIpPoolCredentialDO cred = new ResourceIpPoolCredentialDO();
            cred.setIpId(ipId);
            cred.setSshHost(r.getSshHost());
            cred.setSshPort(r.getSshPort());
            cred.setSshUser(r.getSshUser());
            cred.setSshPassword(r.getSshPassword());
            cred.setCreatedAt(now);
            cred.setUpdatedAt(now);
            credentialMapper.insert(cred);

            // billing 子表 (空占位, admin 后续编辑)
            ResourceIpPoolBillingDO bill = new ResourceIpPoolBillingDO();
            bill.setIpId(ipId);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            billingMapper.insert(bill);

            // socks5 子表 (dante 业务配置)
            ResourceIpPoolSocks5DO socks5 = new ResourceIpPoolSocks5DO();
            socks5.setIpId(ipId);
            socks5.setSocks5Port(r.getSocksPort());
            socks5.setSocks5Username(r.getSocksUser());
            socks5.setSocks5Password(r.getSocksPass());
            socks5.setLogLevel(r.getLogLevel());
            socks5.setBandwidthLimitMbps(0);
            socks5.setCreatedAt(now);
            socks5.setUpdatedAt(now);
            socks5Mapper.insert(socks5);

            // install 子表 (装机产物; 跟 xray_server 同语义)
            ResourceIpPoolInstallDO install = new ResourceIpPoolInstallDO();
            install.setIpId(ipId);
            install.setInstallDir(r.getInstallDir());
            install.setLogPath(r.getLogPath());
            install.setConfPath(r.getConfPath());
            install.setPamFile(r.getPamFile());
            install.setPwdFile(r.getPwdFile());
            install.setSystemdUnit(r.getSystemdUnit());
            install.setAutostartEnabled(Boolean.TRUE.equals(r.getAutostartEnabled()) ? 1 : 0);
            install.setFirewallEnabled(Boolean.TRUE.equals(r.getInstallUfw()) ? 1 : 0);
            install.setLogRotateEnabled(Boolean.TRUE.equals(r.getLogRotate()) ? 1 : 0);
            install.setInstalledAt(now);
            installMapper.insert(install);

            // runtime 子表占位 (agent 接管后由心跳填)
            ResourceIpPoolRuntimeDO runtime = new ResourceIpPoolRuntimeDO();
            runtime.setIpId(ipId);
            runtime.setTempUnhealthy(0);
            runtime.setConsecutiveMiss(0);
            runtime.setUpdatedAt(now);
            runtimeMapper.insert(runtime);

            return ipId;
        });
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

    @Override
    public void syncSocks5Creds(String ipId, ResourceIpSocksSyncCredsReqVO reqVO, Consumer<String> lineSink) {
        // 1. 拉 IP 池, 校验 provisionMode 自部署 + SOCKS5 配置齐
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        if (ip.getProvisionMode() == null || ip.getProvisionMode() != 1) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_NOT_SELF_DEPLOY, ip.getIpAddress());
        }
        ResourceIpPoolSocks5DO socks5 = socks5Mapper.selectById(ipId);
        if (StrUtil.isBlank(ip.getIpAddress()) || socks5 == null
                || ObjectUtil.isNull(socks5.getSocks5Port())
                || StrUtil.isBlank(socks5.getSocks5Username()) || StrUtil.isBlank(socks5.getSocks5Password())) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_SOCKS5_INCOMPLETE, ip.getIpAddress());
        }
        ResourceIpPoolInstallDO install = installMapper.selectById(ipId);
        if (install == null) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_SOCKS5_INCOMPLETE, ip.getIpAddress());
        }

        // 2. SSH 到 landing 跑 update-dante-creds.sh; 走 ad-hoc cred (用户每次输 SSH 密码)
        lineSink.accept("[nook] === 阶段 1/2: 同步 landing dante 配置 ===\n");
        SessionCredential landingCred = buildSyncCred(ip, reqVO);
        // SSH 端口走入参不走 DB 现值; sync 这次握手用的就是 reqVO.sshPort, UFW 跟着放行才合理
        Map<String, String> vars = buildSyncVars(ip, socks5, install, reqVO.getSshPort());
        Duration scriptTimeout = Duration.ofSeconds(reqVO.getInstallTimeoutSeconds());
        SshSessions.runAdHocVoid(landingCred, session ->
                scriptCatalog.run(session, NookScripts.SOCKS5_UPDATE_CREDS, vars, scriptTimeout, lineSink));

        // 反查 xray_client (IP 跟 client 一一对应, 至多 1 行); 没绑 client 就完事
        lineSink.accept("\n[nook] === 阶段 2/2: 重建 fra-line outbound (新凭据生效) ===\n");
        XrayClientDO client = xrayClientMapper.selectByIpId(ipId);
        if (client == null) {
            lineSink.accept("[nook] 无 client 绑定此 IP, 跳过 outbound 重建\n");
            log.info("[ip-pool] SYNC-CREDS ipId={} ip={} 无 client", ipId, ip.getIpAddress());
            return;
        }

        // 4. SSH 到 client 所在 xray server (走 stored cred), 仅 rmo + ado outbound
        XrayServerDO server = xrayServerValidator.validateExists(client.getServerId());
        String outboundTag = client.getId();
        int apiPort = server.getXrayApiPort();
        String xrayBin = server.getXrayBinaryPath();

        lineSink.accept(String.format("[nook] SSH xray server=%s, 重建 outbound tag=%s ...\n",
                client.getServerId(), outboundTag));
        SshSession session = SshSessions.acquire(client.getServerId(), SshSessionScope.SHARED);
        try {
            outboundCli.removeOutbound(session, xrayBin, apiPort, outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) {
                throw be;
            }
            lineSink.accept("[nook] 远端原本无该 outbound, 跳过 rmo\n");
        }
        outboundCli.addSocksOutbound(session, xrayBin, apiPort, outboundTag,
                ip.getIpAddress(), socks5.getSocks5Port(),
                socks5.getSocks5Username(), socks5.getSocks5Password());
        lineSink.accept("[nook] outbound " + outboundTag + " 已用新凭据重建\n");
        lineSink.accept("[nook] === 同步完成 ===\n");
        log.info("[ip-pool] SYNC-CREDS ipId={} ip={} client={} outbound={}",
                ipId, ip.getIpAddress(), client.getId(), outboundTag);
    }

    /** sync-creds 专用 cred: host 自动 = ip.ipAddress, user/password/超时 走入参. */
    private SessionCredential buildSyncCred(ResourceIpPoolDO ip, ResourceIpSocksSyncCredsReqVO r) {
        return SessionCredential.builder()
                .serverId("ad-hoc:" + ip.getIpAddress())
                .sshHost(ip.getIpAddress())
                .sshPort(r.getSshPort())
                .sshUser(r.getSshUser())
                .sshPassword(r.getSshPassword())
                .sshTimeoutSeconds(r.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(r.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(r.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(r.getInstallTimeoutSeconds())
                .build();
    }

    /** sync-creds 模板变量: socks5 业务字段 + install 装机产物联合渲染, sshPort 单独传入. */
    private Map<String, String> buildSyncVars(ResourceIpPoolDO ip, ResourceIpPoolSocks5DO socks5,
                                              ResourceIpPoolInstallDO install, int sshPort) {
        return Map.ofEntries(
                Map.entry("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.entry("SOCKS_PORT", String.valueOf(socks5.getSocks5Port())),
                Map.entry("SSH_PORT", String.valueOf(sshPort)),
                Map.entry("SOCKS_USER", socks5.getSocks5Username()),
                Map.entry("SOCKS_PASS", socks5.getSocks5Password()),
                Map.entry("LOG_LEVEL", socks5.getLogLevel()),
                Map.entry("LOG_PATH", install.getLogPath()),
                Map.entry("INSTALL_DIR", install.getInstallDir()),
                Map.entry("CONF_PATH", install.getConfPath()),
                Map.entry("PWD_FILE", install.getPwdFile()),
                Map.entry("SYSTEMD_UNIT", install.getSystemdUnit()),
                Map.entry("FIREWALL_ENABLED", String.valueOf(install.getFirewallEnabled())),
                Map.entry("AUTOSTART_ENABLED", String.valueOf(install.getAutostartEnabled())));
    }

    /** 把请求里的 ad-hoc SSH 字段封成 SessionCredential; 不入库, 也不绕道业务 DTO. */
    private SessionCredential buildAdHocCred(ResourceIpSocksInstallReqVO r) {
        return SessionCredential.builder()
                .serverId("ad-hoc:" + r.getSshHost())
                .sshHost(r.getSshHost())
                .sshPort(r.getSshPort())
                .sshUser(r.getSshUser())
                .sshPassword(r.getSshPassword())
                .sshTimeoutSeconds(r.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(r.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(r.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(r.getInstallTimeoutSeconds())
                .build();
    }

    /**
     * 模板渲染变量表; 后端不再 blankToDefault, 入参字段全部 @NotBlank/@NotNull 强校验.
     * 命名跟 install-dante-landing.sh.tmpl 里的 {{...}} 占位符对齐.
     */
    private Map<String, String> buildVars(ResourceIpSocksInstallReqVO r) {
        return Map.ofEntries(
                Map.entry("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.entry("SOCKS_PORT", String.valueOf(r.getSocksPort())),
                Map.entry("SSH_PORT", String.valueOf(r.getSshPort())),
                Map.entry("SOCKS_USER", r.getSocksUser()),
                Map.entry("SOCKS_PASS", r.getSocksPass()),
                Map.entry("FIREWALL_ENABLED", Boolean.TRUE.equals(r.getInstallUfw()) ? "1" : "0"),
                Map.entry("LOG_LEVEL", r.getLogLevel()),
                Map.entry("LOG_PATH", r.getLogPath()),
                Map.entry("INSTALL_DIR", r.getInstallDir()),
                Map.entry("CONF_PATH", r.getConfPath()),
                Map.entry("PAM_FILE", r.getPamFile()),
                Map.entry("PWD_FILE", r.getPwdFile()),
                Map.entry("SYSTEMD_UNIT", r.getSystemdUnit()),
                Map.entry("AUTOSTART_ENABLED", Boolean.TRUE.equals(r.getAutostartEnabled()) ? "1" : "0"),
                Map.entry("LOG_ROTATE_ENABLED", Boolean.TRUE.equals(r.getLogRotate()) ? "1" : "0"));
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
