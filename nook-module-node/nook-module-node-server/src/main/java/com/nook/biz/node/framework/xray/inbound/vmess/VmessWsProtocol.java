package com.nook.biz.node.framework.xray.inbound.vmess;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.api.xray.XrayInstallDefaults;
import com.nook.biz.node.controller.xray.vo.XrayInboundConfigVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.framework.cloudflare.CloudflareApiClient;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import com.nook.biz.node.framework.xray.inbound.InboundTemplateRenderer;
import com.nook.biz.node.framework.xray.inbound.InboundProtocol;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionResult;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionRequest;
import com.nook.biz.node.framework.xray.inbound.InboundUserRequest;
import com.nook.biz.node.framework.xray.inbound.ShareContext;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.biz.system.api.domain.DomainUtils;
import com.nook.biz.system.api.domain.SystemDomainApi;
import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * vmess + WebSocket 协议; 绑域名走 tls (含域名解析 + CF A 记录), 不绑走 none
 *
 * @author nook
 */
@Slf4j
@Component
public class VmessWsProtocol implements InboundProtocol {

    /** xray streamSettings.network (vmess 固定 ws); 客户面 vmess net / clash network. */
    private static final String NETWORK_WS = "ws";
    /** 客户面协议名 (vmess:// / clash type). */
    private static final String PROTOCOL_VMESS = "vmess";
    /** vmess 链接 tls 字段值 (绑域名时). */
    private static final String SECURITY_TLS = "tls";

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
    public Set<XrayInboundProtocolEnum> supportedForms() {
        return Set.of(XrayInboundProtocolEnum.VMESS_WS_TLS, XrayInboundProtocolEnum.VMESS_WS_PLAIN);
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
    public InboundProvisionResult provision(InboundProvisionRequest ctx) {
        XrayInboundConfigVO inbound = ctx.getReqVO().getInbound();
        boolean useTls = StrUtil.isNotBlank(inbound.getDomainId());
        String fullDomain = null;
        String cfApiToken = null;
        if (useTls) {
            // 根域 + cfApiToken 从 system_domain 取, 完整 FQDN = 二级标签 + 根域
            SystemDomainRespDTO domain = systemDomainApi.getById(inbound.getDomainId());
            fullDomain = DomainUtils.buildFqdn(inbound.getSubdomain(), domain.getDomain());
            cfApiToken = domain.getCfApiToken();
            this.ensureCfRecord(ctx, fullDomain, cfApiToken);
        }
        XrayInboundProtocolEnum protocol = useTls
                ? XrayInboundProtocolEnum.VMESS_WS_TLS : XrayInboundProtocolEnum.VMESS_WS_PLAIN;
        // 语义参数: ws path + (绑域名时) tls 路径
        VmessWsParams params = new VmessWsParams();
        VmessWsParams.WsParams ws = new VmessWsParams.WsParams();
        ws.setPath(inbound.getWsPath());
        params.setWs(ws);
        if (useTls) {
            VmessWsParams.TlsParams tls = new VmessWsParams.TlsParams();
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
        return InboundProvisionResult.builder()
                .protocol(protocol)
                .params(params)
                .inboundJson(inboundJson)
                .fullDomain(fullDomain)
                .cfApiToken(cfApiToken)
                .build();
    }

    @Override
    public String buildAduJson(String tag, InboundUserRequest user) {
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

    @Override
    public List<String> clientFacingDiff(InboundParams existingParams, XrayInboundConfigVO newInput) {
        VmessWsParams existing = (existingParams instanceof VmessWsParams v) ? v : null;
        List<String> diffs = new ArrayList<>();
        // ws 接入路径
        String oldWsPath = (existing != null && existing.getWs() != null) ? existing.getWs().getPath() : null;
        if (!ObjectUtil.equal(oldWsPath, newInput.getWsPath())) {
            diffs.add("wsPath: " + oldWsPath + " → " + newInput.getWsPath());
        }
        // 对外域名 (FQDN): 绑域名拼完整 FQDN (二级标签 + 根域), 未绑 (domainId 空) 落 null
        String oldDomain = (existing != null && existing.getTls() != null) ? existing.getTls().getDomain() : null;
        String newDomain = StrUtil.isBlank(newInput.getDomainId()) ? null
                : DomainUtils.buildFqdn(newInput.getSubdomain(), systemDomainApi.getById(newInput.getDomainId()).getDomain());
        if (!ObjectUtil.equal(oldDomain, newDomain)) {
            diffs.add("domain: " + oldDomain + " → " + newDomain);
        }
        return diffs;
    }

    @Override
    public String buildShareLink(InboundParams params, ShareContext ctx) {
        VmessWsParams p = (params instanceof VmessWsParams v) ? v : null;
        boolean tls = p != null && p.getTls() != null;
        String domain = tls ? p.getTls().getDomain() : null;
        // 绑域名优先用对外域名, 否则回退线路机出网 IP; 都没有则拼不出连接
        String host = StrUtil.isNotBlank(domain) ? domain : ctx.getServerIp();
        if (StrUtil.isBlank(host)) {
            return null;
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("v", "2");
        v.put("ps", StrUtil.nullToEmpty(ctx.getLabel()));
        v.put("add", host);
        v.put("port", String.valueOf(ctx.getPort()));
        v.put("id", ctx.getUuid());
        v.put("aid", "0");
        v.put("scy", "auto");
        v.put("net", NETWORK_WS);
        v.put("type", "none");
        v.put("host", host);
        v.put("path", StrUtil.nullToEmpty(p != null && p.getWs() != null ? p.getWs().getPath() : null));
        v.put("tls", tls ? SECURITY_TLS : "");
        if (tls) {
            v.put("sni", host);
        }
        return "vmess://" + Base64.encode(JSON.toJSONString(v));
    }

    @Override
    public Map<String, Object> buildClashProxy(InboundParams params, ShareContext ctx) {
        VmessWsParams p = (params instanceof VmessWsParams v) ? v : null;
        boolean tls = p != null && p.getTls() != null;
        String domain = tls ? p.getTls().getDomain() : null;
        String host = StrUtil.isNotBlank(domain) ? domain : ctx.getServerIp();
        if (StrUtil.isBlank(host)) {
            return null;
        }
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", ctx.getLabel());
        proxy.put("type", PROTOCOL_VMESS);
        proxy.put("server", host);
        proxy.put("port", ctx.getPort());
        proxy.put("uuid", ctx.getUuid());
        proxy.put("alterId", 0);
        proxy.put("cipher", "auto");
        proxy.put("udp", true);
        proxy.put("network", NETWORK_WS);
        if (tls) {
            proxy.put("tls", true);
            proxy.put("servername", host);
        }
        Map<String, Object> wsOpts = new LinkedHashMap<>();
        wsOpts.put("path", StrUtil.blankToDefault(p != null && p.getWs() != null ? p.getWs().getPath() : null, "/"));
        wsOpts.put("headers", Map.of("Host", host));
        proxy.put("ws-opts", wsOpts);
        return proxy;
    }

    /** 部署前加 CF A 记录; 失败不阻断 (用户可手动在 CF 面板加). */
    private void ensureCfRecord(InboundProvisionRequest ctx, String fullDomain, String cfApiToken) {
        if (StrUtil.isBlank(cfApiToken)) {
            return;
        }
        try {
            cloudflareApiClient.ensureARecord(cfApiToken, fullDomain, ctx.getServerIp(), false);
            ctx.getLineSink().accept("[nook] ✔ Cloudflare A 记录已加: " + fullDomain + " → " + ctx.getServerIp() + "\n");
        } catch (Exception cfe) {
            ctx.getLineSink().accept("[nook] ⚠ Cloudflare A 记录创建失败 (" + cfe.getMessage()
                    + "), 请手动在 CF 面板加 A 记录\n");
            log.warn("[install] CF API 失败 server={} domain={}: {}", ctx.getServerId(), fullDomain, cfe.getMessage());
        }
    }
}
