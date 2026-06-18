package com.nook.biz.node.framework.xray.inbound.protocol;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.api.xray.XrayInstallDefaults;
import com.nook.biz.node.controller.xray.vo.XrayInboundConfigVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.framework.cloudflare.CloudflareApiClient;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;
import com.nook.biz.node.framework.xray.inbound.config.InboundTemplateRenderer;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.biz.system.api.domain.SystemDomainApi;
import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * vmess + WebSocket 协议; 绑域名走 tls (含域名解析 + CF A 记录), 不绑走 none
 *
 * @author nook
 */
@Slf4j
@Component
public class VmessWsProtocol implements InboundProtocol {

    /** vmess+ws+tls inbound 模板 (${} 占位符 render 时填充). */
    private static final String TEMPLATE_TLS = """
            {"tag":${tag},"listen":${listenIp},"port":${port},"protocol":"vmess","settings":{"clients":[]},"sniffing":{"enabled":true,"destOverride":["http","tls","quic"]},"streamSettings":{"network":"ws","security":"tls","tlsSettings":{"serverName":${domain},"alpn":["h2","http/1.1"],"minVersion":"1.2","certificates":[{"certificateFile":${tls.certPath},"keyFile":${tls.keyPath}}]},"wsSettings":{"path":${ws.path}}}}""";

    /** vmess+ws (无 tls) inbound 模板. */
    private static final String TEMPLATE_PLAIN = """
            {"tag":${tag},"listen":${listenIp},"port":${port},"protocol":"vmess","settings":{"clients":[]},"sniffing":{"enabled":true,"destOverride":["http","tls","quic"]},"streamSettings":{"network":"ws","security":"none","wsSettings":{"path":${ws.path}}}}""";

    @Resource
    private SystemDomainApi systemDomainApi;
    @Resource
    private XrayInstallService xrayInstallService;
    @Resource
    private CloudflareApiClient cloudflareApiClient;
    @Resource
    private InboundTemplateRenderer inboundTemplateRenderer;

    @Override
    public boolean supports(String protocol) {
        return "vmess".equalsIgnoreCase(protocol);
    }

    @Override
    public void validate(String serverId, XrayInstallReqVO reqVO) {
        XrayInboundConfigVO inbound = reqVO.getInbound();
        if (StrUtil.isBlank(inbound.getWsPath())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "vmess+ws 装机 wsPath 必填");
        }
        // 未绑域名 = 纯 ws, 无需域名/证书校验
        if (StrUtil.isBlank(inbound.getDomainId())) {
            return;
        }
        systemDomainApi.getById(inbound.getDomainId());
        if (StrUtil.isBlank(inbound.getSubdomain())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "绑定域名时二级域名 (subdomain) 必填");
        }
        if (xrayInstallService.isSubdomainTaken(inbound.getDomainId(), inbound.getSubdomain().trim(), serverId)) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID,
                    "该根域下二级域名 '" + inbound.getSubdomain().trim() + "' 已被其他线路机占用, 请换一个");
        }
    }

    @Override
    public InboundProvision provision(InboundProvisionContext ctx) {
        XrayInboundConfigVO inbound = ctx.reqVO().getInbound();
        boolean useTls = StrUtil.isNotBlank(inbound.getDomainId());
        String fullDomain = null;
        String cfApiToken = null;
        if (useTls) {
            // 根域 + cfApiToken 从 system_domain 取, 完整 FQDN = 二级标签 + 根域
            SystemDomainRespDTO domain = systemDomainApi.getById(inbound.getDomainId());
            fullDomain = XrayConstants.fqdn(inbound.getSubdomain(), domain.getDomain());
            cfApiToken = domain.getCfApiToken();
            this.ensureCfRecord(ctx, fullDomain, cfApiToken);
        }
        XrayInboundProtocolEnum protocol = useTls
                ? XrayInboundProtocolEnum.VMESS_WS_TLS : XrayInboundProtocolEnum.VMESS_WS_PLAIN;
        // 语义参数: ws path + (绑域名时) tls 路径
        InboundParams params = new InboundParams();
        InboundParams.WsParams ws = new InboundParams.WsParams();
        ws.setPath(inbound.getWsPath());
        params.setWs(ws);
        if (useTls) {
            InboundParams.TlsParams tls = new InboundParams.TlsParams();
            tls.setCertPath(XrayInstallDefaults.TLS_CERT_PATH);
            tls.setKeyPath(XrayInstallDefaults.TLS_KEY_PATH);
            tls.setDomain(fullDomain);
            params.setTls(tls);
        }
        // 模板占位符
        Map<String, Object> vars = new HashMap<>();
        vars.put("tag", XrayConstants.SHARED_INBOUND_TAG);
        vars.put("listenIp", inbound.getListenIp());
        vars.put("port", inbound.getSharedInboundPort());
        vars.put("ws.path", inbound.getWsPath());
        if (useTls) {
            vars.put("domain", fullDomain);
            vars.put("tls.certPath", XrayInstallDefaults.TLS_CERT_PATH);
            vars.put("tls.keyPath", XrayInstallDefaults.TLS_KEY_PATH);
        }
        String inboundJson = inboundTemplateRenderer.render(useTls ? TEMPLATE_TLS : TEMPLATE_PLAIN, vars);
        return new InboundProvision(protocol, params, inboundJson, fullDomain, cfApiToken);
    }

    @Override
    public String buildAduJson(String tag, InboundUserSpec user) {
        JSONObject client = new JSONObject();
        client.put("id", user.getUuid());
        client.put("email", user.getEmail());
        client.put("level", 0);
        JSONArray clients = new JSONArray();
        clients.add(client);
        JSONObject settings = new JSONObject();
        settings.put("clients", clients);
        JSONObject inbound = new JSONObject();
        inbound.put("tag", tag);
        inbound.put("listen", "0.0.0.0");
        inbound.put("port", 1);
        inbound.put("protocol", "vmess");
        inbound.put("settings", settings);
        JSONArray inbounds = new JSONArray();
        inbounds.add(inbound);
        JSONObject config = new JSONObject();
        config.put("inbounds", inbounds);
        return config.toJSONString();
    }

    /** 部署前加 CF A 记录; 失败不阻断 (用户可手动在 CF 面板加). */
    private void ensureCfRecord(InboundProvisionContext ctx, String fullDomain, String cfApiToken) {
        if (StrUtil.isBlank(cfApiToken)) {
            return;
        }
        try {
            cloudflareApiClient.ensureARecord(cfApiToken, fullDomain, ctx.serverIp(), false);
            ctx.lineSink().accept("[nook] ✔ Cloudflare A 记录已加: " + fullDomain + " → " + ctx.serverIp() + "\n");
        } catch (Exception cfe) {
            ctx.lineSink().accept("[nook] ⚠ Cloudflare A 记录创建失败 (" + cfe.getMessage()
                    + "), 请手动在 CF 面板加 A 记录\n");
            log.warn("[install] CF API 失败 server={} domain={}: {}", ctx.serverId(), fullDomain, cfe.getMessage());
        }
    }
}
