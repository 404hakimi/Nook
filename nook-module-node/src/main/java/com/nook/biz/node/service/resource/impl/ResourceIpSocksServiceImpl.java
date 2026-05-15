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
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.enums.ResourceErrorCode;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.server.script.config.RemoteScriptPaths;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.framework.xray.cli.XrayOutboundCli;
import com.nook.biz.node.service.resource.ResourceIpSocksService;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.biz.node.validator.XrayNodeValidator;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

/**
 * SOCKS5 落地节点 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceIpSocksServiceImpl implements ResourceIpSocksService {

    @Resource
    private RemoteScriptRunner scriptRunner;
    @Resource
    private Socks5Prober socks5Prober;
    @Resource
    private ResourceIpPoolValidator ipPoolValidator;
    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayNodeValidator xrayNodeValidator;
    @Resource
    private XrayOutboundCli outboundCli;
    @Resource
    private ServerProbe serverProbe;
    @Resource
    private ResourceIpPoolMapper resourceIpPoolMapper;

    /** dante 的 systemd unit 名 (apt 包默认), 跟 install / update 脚本里 systemctl 操作的 unit 对齐. */
    private static final String DANTE_UNIT = "danted";

    @Override
    public void installSocks5(ResourceIpSocksInstallReqVO reqVO, Consumer<String> lineSink) {
        // ad-hoc 凭据来自前端表单, 不入 resource_server, 直接构造 framework 值对象绕开业务 DTO
        SessionCredential cred = buildAdHocCred(reqVO);
        Map<String, String> vars = buildVars(reqVO);
        Duration installTimeout = Duration.ofSeconds(reqVO.getInstallTimeoutSeconds());
        SshSessions.runAdHocVoid(cred, session ->
                scriptRunner.runFromTemplateStreaming(
                        session, RemoteScriptPaths.SOCKS5_INSTALL_TMPL, vars,
                        RemoteScriptPaths.INSTALL_SOCKS5_TMP, installTimeout, lineSink));
    }

    @Override
    public ResourceIpSocksTestRespVO testSocks5(String ipId, ResourceIpSocksTestReqVO reqVO) {
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        if (StrUtil.isBlank(ip.getIpAddress()) || ObjectUtil.isNull(ip.getSocks5Port())) {
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
                ip.getIpAddress(), ip.getSocks5Port(), ip.getSocks5Username(), ip.getSocks5Password(),
                reqVO.getEchoUrl(), reqVO.getConnectTimeoutMs(), reqVO.getReadTimeoutMs());
        return Socks5OpsConvert.INSTANCE.convert(snap);
    }

    @Override
    public void syncSocks5Creds(String ipId, ResourceIpSocksSyncCredsReqVO reqVO, Consumer<String> lineSink) {
        // 1. 拉 IP 池, 校验 provisionMode 自部署 + SOCKS5 配置齐
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        if (ip.getProvisionMode() == null || ip.getProvisionMode() != 1) {
            throw new BusinessException(com.nook.biz.node.enums.ResourceErrorCode.IP_POOL_NOT_SELF_DEPLOY,
                    ip.getIpAddress());
        }
        if (StrUtil.isBlank(ip.getIpAddress()) || ObjectUtil.isNull(ip.getSocks5Port())
                || StrUtil.isBlank(ip.getSocks5Username()) || StrUtil.isBlank(ip.getSocks5Password())) {
            throw new BusinessException(com.nook.biz.node.enums.ResourceErrorCode.IP_POOL_SOCKS5_INCOMPLETE,
                    ip.getIpAddress());
        }

        // 2. SSH 到 landing 跑 update-dante-creds.sh; 走 ad-hoc cred (用户每次输 SSH 密码)
        lineSink.accept("[nook] === 阶段 1/2: 同步 landing dante 配置 ===\n");
        SessionCredential landingCred = buildSyncCred(ip, reqVO);
        // SSH 端口走入参不走 DB 现值; sync 这次握手用的就是 reqVO.sshPort, UFW 跟着放行才合理
        Map<String, String> vars = buildSyncVars(ip, reqVO.getSshPort());
        Duration scriptTimeout = Duration.ofSeconds(reqVO.getInstallTimeoutSeconds());
        SshSessions.runAdHocVoid(landingCred, session ->
                scriptRunner.runFromTemplateStreaming(
                        session, RemoteScriptPaths.SOCKS5_UPDATE_CREDS_TMPL, vars,
                        RemoteScriptPaths.UPDATE_SOCKS5_CREDS_TMP, scriptTimeout, lineSink));

        // 3. 反查 xray_client (1:1 模型, 至多 1 行); 没绑 client 就完事
        lineSink.accept("\n[nook] === 阶段 2/2: 重建 fra-line outbound (新凭据生效) ===\n");
        XrayClientDO client = xrayClientMapper.selectByIpId(ipId);
        if (client == null) {
            lineSink.accept("[nook] 无 client 绑定此 IP, 跳过 outbound 重建\n");
            log.info("[ip-pool] SYNC-CREDS ipId={} ip={} 无 client", ipId, ip.getIpAddress());
            return;
        }

        // 4. SSH 到 client 所在 xray server (走 stored cred), 仅 rmo + ado outbound
        XrayNodeDO node = xrayNodeValidator.validateExists(client.getServerId());
        String outboundTag = "out_slot_" + String.format("%02d", client.getSlotIndex());
        int apiPort = node.getXrayApiPort();

        lineSink.accept(String.format("[nook] SSH xray server=%s, 重建 outbound tag=%s ...\n",
                client.getServerId(), outboundTag));
        SshSession session = SshSessions.acquire(client.getServerId(), SshSessionScope.SHARED);
        try {
            outboundCli.removeOutbound(session, apiPort, outboundTag);
        } catch (BusinessException be) {
            // 远端原本没有就当作已删, 不报错
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) {
                throw be;
            }
            lineSink.accept("[nook] 远端原本无该 outbound, 跳过 rmo\n");
        }
        outboundCli.addSocksOutbound(session, apiPort, outboundTag,
                ip.getIpAddress(), ip.getSocks5Port(),
                ip.getSocks5Username(), ip.getSocks5Password());
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

    /** sync-creds 模板变量: 全部从 DB 现值读, sshPort 单独传入 (跟当前 SSH 握手用的端口对齐). */
    private Map<String, String> buildSyncVars(ResourceIpPoolDO ip, int sshPort) {
        int autostart = ip.getAutostartEnabled() == null ? 1 : ip.getAutostartEnabled();
        int firewall = ip.getFirewallEnabled() == null ? 1 : ip.getFirewallEnabled();
        String installDir = StrUtil.blankToDefault(ip.getInstallDir(), DEFAULT_INSTALL_DIR);
        return Map.ofEntries(
                Map.entry("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.entry("SOCKS_PORT", String.valueOf(ip.getSocks5Port())),
                Map.entry("SSH_PORT", String.valueOf(sshPort)),
                Map.entry("SOCKS_USER", ip.getSocks5Username()),
                Map.entry("SOCKS_PASS", ip.getSocks5Password()),
                Map.entry("ALLOW_FROM", StrUtil.blankToDefault(ip.getFirewallAllowFrom(), "0.0.0.0/0")),
                Map.entry("FIREWALL_ENABLED", String.valueOf(firewall)),
                Map.entry("LOG_LEVEL", StrUtil.blankToDefault(ip.getLogLevel(), DEFAULT_LOG_LEVEL)),
                Map.entry("LOG_PATH", StrUtil.isBlank(ip.getLogPath())
                        ? installDir + "/logs/sockd.log" : ip.getLogPath()),
                Map.entry("INSTALL_DIR", installDir),
                Map.entry("AUTOSTART_ENABLED", String.valueOf(autostart)));
    }

    /** 把请求里的 ad-hoc SSH 字段封成 SessionCredential; 不入库, 也不绕道业务 DTO. */
    private SessionCredential buildAdHocCred(ResourceIpSocksInstallReqVO r) {
        return SessionCredential.builder()
                // serverId 仅供日志识别, 不参与 DB 查询
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
     * 模板渲染变量表; 字段缺省用 dante 部署的合理默认值, 上层 dialog 留空也能跑.
     * 命名跟 install-dante-landing.sh.tmpl 里的 {{...}} 占位符对齐.
     */
    private Map<String, String> buildVars(ResourceIpSocksInstallReqVO r) {
        boolean autostart = r.getAutostartEnabled() == null || r.getAutostartEnabled();
        boolean firewall = r.getInstallUfw() != null && r.getInstallUfw();
        String installDir = StrUtil.blankToDefault(r.getInstallDir(), DEFAULT_INSTALL_DIR);
        return Map.ofEntries(
                Map.entry("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.entry("SOCKS_PORT", String.valueOf(r.getSocksPort())),
                // SSH_PORT 是本次握手用的端口; ufw 跟着放行才不会把自己锁外面
                Map.entry("SSH_PORT", String.valueOf(r.getSshPort())),
                Map.entry("SOCKS_USER", r.getSocksUser()),
                Map.entry("SOCKS_PASS", r.getSocksPass()),
                Map.entry("ALLOW_FROM", StrUtil.blankToDefault(r.getAllowFrom(), "0.0.0.0/0")),
                Map.entry("FIREWALL_ENABLED", firewall ? "1" : "0"),
                Map.entry("LOG_LEVEL", StrUtil.blankToDefault(r.getLogLevel(), DEFAULT_LOG_LEVEL)),
                // LOG_PATH 兜底规则: 用户留空 → $INSTALL_DIR/logs/sockd.log (跟 xray 安装目录习惯保持一致)
                Map.entry("LOG_PATH", StrUtil.isBlank(r.getLogPath())
                        ? installDir + "/logs/sockd.log" : r.getLogPath()),
                Map.entry("INSTALL_DIR", installDir),
                Map.entry("AUTOSTART_ENABLED", autostart ? "1" : "0"));
    }

    // ===== status / autostart / log: 走 IP 池条目存储的 SSH 凭据, 不需要前端再输 =====

    @Override
    public Socks5StatusRespVO getSocks5Status(String ipId) {
        ResourceIpPoolDO ip = ipPoolValidator.validateExists(ipId);
        SessionCredential cred = buildStoredCred(ip);
        return SshSessions.runAdHoc(cred, session -> {
            SystemdStatusSnapshot sysd = serverProbe.readSystemdStatus(session, DANTE_UNIT);
            String version = readDanteVersion(session);
            String listening = readDanteListening(session, ip.getSocks5Port());
            // UFW + 主机信息: 一次状态刷新顺手取全, 减少前端二次刷
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

    /** snapshot → respVO 平铺复制; 字段同名, 直接 set. */
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
            // systemctl enable/disable 退出码非 0 是常态 (disabled/static), 不抛错
            String cmd = (enabled ? "systemctl enable " : "systemctl disable ") + DANTE_UNIT
                    + " 2>&1 || true";
            session.ssh().exec(cmd);
        });

        // 同步 DB.autostart_enabled, 让列表 / 编辑表单展示跟远端实际一致
        ResourceIpPoolDO update = new ResourceIpPoolDO();
        update.setId(ipId);
        update.setAutostartEnabled(enabled ? 1 : 0);
        resourceIpPoolMapper.updateById(update);
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
        // 实际 log 文件路径: DB.log_path 留空时按 install_dir 兜底, 跟脚本渲染规则保持一致
        String installDir = StrUtil.blankToDefault(ip.getInstallDir(), DEFAULT_INSTALL_DIR);
        String filePath = StrUtil.isBlank(ip.getLogPath())
                ? installDir + "/logs/sockd.log" : ip.getLogPath();
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
     * 从 IP 池条目里取存储的 SSH 凭据组装 SessionCredential.
     * 缺省策略: sshHost 留空 → 用 ipAddress; sshPort 留空 → 22; sshUser 留空 → root.
     * 密码必须有值, 否则抛业务错让用户回编辑表单补.
     */
    private SessionCredential buildStoredCred(ResourceIpPoolDO ip) {
        if (StrUtil.isBlank(ip.getSshPassword())) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_SSH_CRED_MISSING, ip.getIpAddress());
        }
        String host = StrUtil.blankToDefault(ip.getSshHost(), ip.getIpAddress());
        int port = ObjectUtil.defaultIfNull(ip.getSshPort(), 22);
        String user = StrUtil.blankToDefault(ip.getSshUser(), "root");
        return SessionCredential.builder()
                .serverId("ip-pool:" + ip.getId())
                .sshHost(host)
                .sshPort(port)
                .sshUser(user)
                .sshPassword(ip.getSshPassword())
                // 详情/日志/切自启都是短命令, 超时复用 install / op 一档配置即可
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

    /**
     * ss -ltn 过滤 socks5 端口监听行; 与 XrayDaemonProbe 风格保持一致, 多行字符串前端展示.
     * 端口缺失或 ss 失败时回 "(未捕获)" 让前端有兜底显示.
     */
    private String readDanteListening(SshSession session, Integer socksPort) {
        if (socksPort == null) return "";
        // -H 头不输出, 直接 grep 端口; awk 防 ":1080" 误命中 "11080"
        String cmd = "ss -ltn 2>/dev/null | awk 'NR==1 || $4 ~ /:" + socksPort + "$/' || true";
        String out = session.ssh().exec(cmd).getStdout();
        return out == null ? "" : out.trim();
    }

    private static final String DEFAULT_INSTALL_DIR = "/home/socks5";
    private static final String DEFAULT_LOG_LEVEL = "connect disconnect error";
}
