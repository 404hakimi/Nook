package com.nook.biz.node.service.socks5;

import jakarta.annotation.Resource;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.controller.socks5.vo.Socks5InstallReqVO;
import com.nook.biz.node.controller.socks5.vo.Socks5TestRespVO;
import com.nook.biz.node.convert.socks5.Socks5OpsConvert;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.framework.ssh.SshSessionManager;
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

    /** 安装超时; apt 拉包慢可能要几分钟, 给 10 分钟兜底. */
    private static final Duration INSTALL_TIMEOUT = Duration.ofSeconds(600);

    private static final String TMPL_INSTALL_SOCKS5 = "scripts/install-socks5-landing.sh.tmpl";
    private static final String TMP_PREFIX = "nook-install-socks5";

    @Resource
    private SshSessionManager sessionManager;
    @Resource
    private RemoteScriptRunner scriptRunner;
    @Resource
    private Socks5Prober socks5Prober;
    @Resource
    private ResourceIpPoolApi resourceIpPoolApi;

    @Override
    public void installAdHocStreaming(Socks5InstallReqVO reqVO, Consumer<String> lineSink) {
        // sshPassword 已在 ReqVO 上 @NotBlank, controller 校验拦截; 这里直接走流程
        ServerCredentialDTO cred = buildAdHocCred(reqVO);
        Map<String, String> vars = buildVars(reqVO);
        sessionManager.runAdHocVoid(cred, session ->
                scriptRunner.runFromTemplateStreaming(
                        session, TMPL_INSTALL_SOCKS5, vars, TMP_PREFIX, INSTALL_TIMEOUT, lineSink));
    }

    @Override
    public Socks5TestRespVO testConnectivity(String ipId) {
        IpPoolEntryDTO ip = resourceIpPoolApi.loadEntry(ipId);
        if (StrUtil.isBlank(ip.getSocks5Host()) || ObjectUtil.isNull(ip.getSocks5Port())) {
            // 凭据未配置时不调 prober, 直接返回结构化失败 (与"拨号失败"区分)
            Socks5TestRespVO vo = new Socks5TestRespVO();
            vo.setSuccess(false);
            vo.setError("SOCKS5 主机或端口未配置");
            return vo;
        }
        Socks5ProbeSnapshot snap = socks5Prober.probe(
                ip.getSocks5Host(), ip.getSocks5Port(), ip.getSocks5Username(), ip.getSocks5Password());
        return Socks5OpsConvert.INSTANCE.convert(snap);
    }

    /** 把请求里的 ad-hoc SSH 字段封成 ServerCredentialDTO; 不入库, 只为统一调用 SshSessionManager. */
    private ServerCredentialDTO buildAdHocCred(Socks5InstallReqVO r) {
        return ServerCredentialDTO.builder()
                .serverId("ad-hoc:" + r.getSshHost())  // 仅供日志识别, 不参与 DB 查询
                .sshHost(r.getSshHost())
                .sshPort(r.getSshPort())
                .sshUser(r.getSshUser())
                .sshPassword(r.getSshPassword())
                .sshTimeoutSeconds(r.getSshTimeoutSeconds())
                .build();
    }

    /** 模板渲染变量表 (RENDER_AT/SOCKS_PORT/...); ALLOW_FROM 默认 0.0.0.0/0, INSTALL_UFW 默认 false. */
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
