package com.nook.biz.node.framework.xray.install;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.xray.XrayInstallDefaults;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionResult;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.ssh.script.ScriptModule;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Xray 装机脚本组装: 按 reqVO 勾选项 + 协议产出, 渲染模板变量并拼出经 agent 本地执行的完整脚本
 *
 * @author nook
 */
@Component
public class XrayInstallScriptAssembler {

    @Resource
    private ScriptCatalog scriptCatalog;

    /**
     * 组装完整安装脚本
     *
     * @param serverId 服务器ID (脚本 SERVER_NAME 展示)
     * @param reqVO    装机入参 (勾选项 / 版本 / inbound 端口等)
     * @param prov     协议产出 (形态 / 模板占位符 / 域名)
     * @return 拼好的完整 bash 脚本文本
     */
    public String assemble(String serverId, XrayInstallReqVO reqVO, InboundProvisionResult prov) {
        Map<String, String> vars = this.buildVars(serverId, reqVO, prov);
        return this.assembleModules(reqVO, prov, vars);
    }

    /**
     * 按勾选项拼模块.
     *
     * <p>必装: 00-prepare-env / 48-journald / 50-xray / 99-finalize.
     * 可选: 10-timezone (setTimezone) / 40-ufw (installUfw) / 45-acme-tls (绑域名) / 47-logrotate.
     */
    private String assembleModules(XrayInstallReqVO r, InboundProvisionResult prov, Map<String, String> vars) {
        List<ScriptModule> modules = new ArrayList<>();
        modules.add(NookScripts.MODULE_PREPARE_ENV);
        if (Boolean.TRUE.equals(r.getSetTimezone())) modules.add(NookScripts.MODULE_TIMEZONE);
        if (Boolean.TRUE.equals(r.getInstallUfw()))  modules.add(NookScripts.MODULE_UFW);
        if (StrUtil.isNotBlank(prov.getFullDomain()))   modules.add(NookScripts.MODULE_ACME_TLS);
        if (Boolean.TRUE.equals(r.getLogRotate()))   modules.add(NookScripts.MODULE_LOGROTATE);
        // journald 容量上限是系统级安全网, 防 service stderr/启停日志撑爆磁盘; 无条件加
        modules.add(NookScripts.MODULE_JOURNALD_CAP);
        modules.add(NookScripts.MODULE_XRAY);
        modules.add(NookScripts.MODULE_FINALIZE);
        return scriptCatalog.assemble(modules, vars);
    }

    /**
     * 渲染模板变量表; 基础设施参数 (端口/路径/日志/重启) 走 XrayInstallDefaults 固定默认, inbound 由协议模板渲染成 base64.
     */
    private Map<String, String> buildVars(String serverId, XrayInstallReqVO r, InboundProvisionResult prov) {
        String fullDomain = prov.getFullDomain();
        boolean useTls = StrUtil.isNotBlank(fullDomain);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", StrUtil.blankToDefault(serverId, "<unset>"));
        vars.put("RENDER_AT", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        vars.put("TIMEZONE", Boolean.TRUE.equals(r.getSetTimezone()) ? "Asia/Shanghai" : "");
        vars.put("INSTALL_UFW", String.valueOf(Boolean.TRUE.equals(r.getInstallUfw())));
        vars.put("XRAY_VERSION", r.getXrayVersion());
        vars.put("XRAY_API_PORT", String.valueOf(XrayInstallDefaults.API_PORT));
        vars.put("INSTALL_DIR", XrayInstallDefaults.INSTALL_DIR);
        vars.put("LOG_DIR", XrayInstallDefaults.LOG_DIR);
        vars.put("LOG_LEVEL", XrayInstallDefaults.LOG_LEVEL);
        vars.put("RESTART_POLICY", XrayInstallDefaults.RESTART_POLICY);
        vars.put("ENABLE_ON_BOOT", String.valueOf(Boolean.TRUE.equals(r.getEnableOnBoot())));
        vars.put("FORCE_REINSTALL", String.valueOf(Boolean.TRUE.equals(r.getForceReinstall())));
        vars.put("SHARED_INBOUND_PORT", String.valueOf(r.getInbound().getSharedInboundPort()));
        vars.put("WS_PATH", StrUtil.blankToDefault(r.getInbound().getWsPath(), ""));
        vars.put("XRAY_BINARY_PATH",       XrayInstallDefaults.XRAY_BINARY_PATH);
        vars.put("XRAY_CONFIG_PATH",       XrayInstallDefaults.XRAY_CONFIG_PATH);
        vars.put("XRAY_SHARE_DIR",         XrayInstallDefaults.XRAY_SHARE_DIR);
        vars.put("XRAY_SYSTEMD_UNIT_PATH", XrayInstallDefaults.SYSTEMD_UNIT_PATH);
        vars.put("USE_TLS", String.valueOf(useTls));
        vars.put("DOMAIN", useTls ? fullDomain : "");
        vars.put("CF_API_TOKEN", useTls ? StrUtil.blankToDefault(prov.getCfApiToken(), "") : "");
        vars.put("TLS_CERT_PATH",  useTls ? XrayInstallDefaults.TLS_CERT_PATH : "");
        vars.put("TLS_KEY_PATH",   useTls ? XrayInstallDefaults.TLS_KEY_PATH : "");
        // in_shared inbound 由协议实现渲染好 (${} 模板 + 占位符值均在实现类内), 这里直接 base64 下发
        vars.put("SHARED_INBOUND_B64", Base64.getEncoder()
                .encodeToString(prov.getInboundJson().getBytes(StandardCharsets.UTF_8)));
        // 部署总结展示用: 协议形态标识 (vmess-ws-tls / vmess-ws-plain / vless-reality)
        vars.put("PROTOCOL_DESC", prov.getProtocol().getKey());
        return vars;
    }
}
