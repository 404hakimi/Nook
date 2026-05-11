package com.nook.biz.node.service.socks5;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.socks5.vo.Socks5InstallReqVO;
import com.nook.biz.node.controller.socks5.vo.Socks5TestRespVO;
import com.nook.biz.node.convert.socks5.Socks5OpsConvert;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.server.script.config.RemoteScriptPaths;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.resource.entity.ResourceIpPool;
import com.nook.biz.node.resource.service.ResourceIpPoolService;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSessionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class Socks5OpsServiceImpl implements Socks5OpsService {

    @Resource
    private SshSessionManager sessionManager;
    @Resource
    private RemoteScriptRunner scriptRunner;
    @Resource
    private Socks5Prober socks5Prober;
    @Resource
    private ResourceIpPoolService resourceIpPoolService;

    @Override
    public void installAdHocStreaming(Socks5InstallReqVO reqVO, Consumer<String> lineSink) {
        // ad-hoc 凭据来自前端表单, 不入 resource_server, 直接构造 framework 值对象绕开业务 DTO
        SessionCredential cred = buildAdHocCred(reqVO);
        Map<String, String> vars = buildVars(reqVO);
        Duration installTimeout = Duration.ofSeconds(reqVO.getInstallTimeoutSeconds());
        sessionManager.runAdHocVoid(cred, session ->
                scriptRunner.runFromTemplateStreaming(
                        session, RemoteScriptPaths.SOCKS5_INSTALL_TMPL, vars,
                        RemoteScriptPaths.INSTALL_SOCKS5_TMP, installTimeout, lineSink));
    }

    @Override
    public Socks5TestRespVO testConnectivity(String ipId) {
        ResourceIpPool ip = resourceIpPoolService.findById(ipId);
        if (StrUtil.isBlank(ip.getIpAddress()) || ObjectUtil.isNull(ip.getSocks5Port())) {
            // 凭据未配置时不调 prober, 直接返回结构化失败 (与"拨号失败"区分)
            Socks5TestRespVO vo = new Socks5TestRespVO();
            vo.setSuccess(false);
            vo.setError("SOCKS5 IP 或端口未配置");
            return vo;
        }
        Socks5ProbeSnapshot snap = socks5Prober.probe(
                ip.getIpAddress(), ip.getSocks5Port(), ip.getSocks5Username(), ip.getSocks5Password());
        return Socks5OpsConvert.INSTANCE.convert(snap);
    }

    /** 把请求里的 ad-hoc SSH 字段封成 SessionCredential; 不入库, 也不绕道业务 DTO. */
    private SessionCredential buildAdHocCred(Socks5InstallReqVO r) {
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

    /** 模板渲染变量表 (RENDER_AT / SOCKS_PORT / ...); ALLOW_FROM 默认 0.0.0.0/0. */
    private Map<String, String> buildVars(Socks5InstallReqVO r) {
        return Map.of(
                "RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "SOCKS_PORT", String.valueOf(r.getSocksPort()),
                "SOCKS_USER", r.getSocksUser(),
                "SOCKS_PASS", r.getSocksPass(),
                "ALLOW_FROM", StrUtil.blankToDefault(r.getAllowFrom(), "0.0.0.0/0"),
                "INSTALL_UFW", String.valueOf(r.getInstallUfw() != null && r.getInstallUfw()));
    }
}
