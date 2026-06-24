package com.nook.biz.node.framework.xray.inbound.vless;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.InboundFieldSchema;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import com.nook.biz.node.framework.xray.inbound.InboundSetupSpec;
import com.nook.biz.node.framework.xray.inbound.InboundTemplateRenderer;
import com.nook.biz.node.framework.xray.inbound.InboundProtocol;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionResult;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionRequest;
import com.nook.biz.node.framework.xray.inbound.InboundUserRequest;
import com.nook.biz.node.framework.xray.inbound.ShareContext;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * vless + Vision + REALITY 协议; 装机生成 x25519 密钥, dest 取自候选或自定义主机名
 *
 * @author nook
 */
@Component
public class VlessRealityProtocol implements InboundProtocol {

    /** 固定流控 + uTLS 指纹. */
    private static final String FLOW_VISION = "xtls-rprx-vision";
    private static final String FINGERPRINT_CHROME = "chrome";
    /** 客户面传输 (reality 固定 tcp); vless type / clash network. */
    private static final String NETWORK_TCP = "tcp";
    /** 客户面协议名 (vless:// / clash type). */
    private static final String PROTOCOL_VLESS = "vless";
    /** shortId 字节数 (hex 长度 = 2×). */
    private static final int SHORTID_BYTES = 8;
    /** dest 固定 443 端口 (TLS 站). */
    private static final int REALITY_DEST_PORT = 443;
    /** 偷取目标主机名格式 (FQDN, 不含端口); 预设候选与自定义输入统一按此校验. */
    private static final Pattern HOST_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))+$");
    /** vless+reality inbound 模板 (${} 占位符 render 时填充). */
    private static final String INBOUND_TEMPLATE = """
            {"tag":${tag},"listen":"0.0.0.0","port":${port},"protocol":"vless","settings":{"clients":[],"decryption":"none"},"sniffing":{"enabled":true,"destOverride":["http","tls","quic"]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"show":false,"dest":${reality.dest},"xver":0,"serverNames":${reality.serverNames},"privateKey":${reality.privateKey},"shortIds":${reality.shortIds}}}}""";

    @Resource
    private VlessRealityKeyGenerator vlessRealityKeyGenerator;
    @Resource
    private InboundTemplateRenderer inboundTemplateRenderer;

    @Override
    public Set<XrayInboundProtocolEnum> supportedForms() {
        return Set.of(XrayInboundProtocolEnum.VLESS_REALITY);
    }

    @Override
    public void validate(String serverId, InboundSetupSpec spec) {
        // realityDest = 偷取目标真站主机名 (预设候选或自定义输入); 仅校验主机名格式, 不强制是预设之一
        String dest = StrUtil.trimToNull(input(spec).getRealityDest());
        if (dest == null || !HOST_PATTERN.matcher(dest).matches()) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID,
                    "reality 装机 realityDest 必填且须是合法主机名 (如 www.bing.com)");
        }
    }

    /** 取本协议专属输入 DTO; 工厂已按 protocol 分派 + Jackson 已按 protocol 绑定子类型, 不符 = 请求 protocol 与 params 不一致. */
    private VlessRealityInput input(InboundSetupSpec spec) {
        if (spec.getParams() instanceof VlessRealityInput v) {
            return v;
        }
        throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "vless 入参类型不匹配 (protocol 与 params 不一致)");
    }

    @Override
    public InboundProvisionResult provision(InboundProvisionRequest ctx) {
        InboundSetupSpec spec = ctx.getSpec();
        String serverName = input(spec).getRealityDest().trim();
        VlessRealityKeyGenerator.RealityKeyPair keyPair = vlessRealityKeyGenerator.generateKeyPair();
        // 语义参数: reality 密钥 + dest (privateKey 进服务端, publicKey 进订阅)
        VlessRealityParams params = new VlessRealityParams();
        params.setFlow(FLOW_VISION);
        VlessRealityParams.RealityParams reality = new VlessRealityParams.RealityParams();
        reality.setDest(serverName + ":" + REALITY_DEST_PORT);
        reality.setServerNames(List.of(serverName));
        reality.setPrivateKey(keyPair.privateKey());
        reality.setPublicKey(keyPair.publicKey());
        reality.setShortIds(List.of("", vlessRealityKeyGenerator.generateShortId(SHORTID_BYTES)));
        reality.setFingerprint(FINGERPRINT_CHROME);
        params.setReality(reality);
        // 模板占位符
        Map<String, Object> vars = new HashMap<>();
        vars.put("tag", XrayConstants.SHARED_INBOUND_TAG);
        vars.put("port", spec.getSharedInboundPort());
        vars.put("reality.dest", reality.getDest());
        vars.put("reality.serverNames", reality.getServerNames());
        vars.put("reality.privateKey", reality.getPrivateKey());
        vars.put("reality.shortIds", reality.getShortIds());
        String inboundJson = inboundTemplateRenderer.render(INBOUND_TEMPLATE, vars);
        return InboundProvisionResult.builder()
                .protocol(XrayInboundProtocolEnum.VLESS_REALITY)
                .params(params)
                .inboundJson(inboundJson)
                .build();
    }

    @Override
    public String buildAduJson(String tag, InboundUserRequest user) {
        JSONObject client = new JSONObject();
        client.put("id", user.getUuid());
        client.put("email", user.getEmail());
        // flow 是本协议固定形态 (xtls-rprx-vision), 由协议自己提供, 不依赖调用方传入
        client.put("flow", FLOW_VISION);
        client.put("level", 0);
        JSONArray clients = new JSONArray();
        clients.add(client);
        JSONObject settings = new JSONObject();
        settings.put("clients", clients);
        settings.put("decryption", "none");
        JSONObject inbound = new JSONObject();
        inbound.put("tag", tag);
        inbound.put("listen", "0.0.0.0");
        inbound.put("port", 1);
        inbound.put("protocol", "vless");
        inbound.put("settings", settings);
        JSONArray inbounds = new JSONArray();
        inbounds.add(inbound);
        JSONObject config = new JSONObject();
        config.put("inbounds", inbounds);
        return config.toJSONString();
    }

    @Override
    public List<String> clientFacingDiff(InboundParams existingParams, InboundSetupSpec newInput) {
        VlessRealityParams existing = (existingParams instanceof VlessRealityParams v) ? v : null;
        List<String> diffs = new ArrayList<>();
        // realityDest (偷取的目标真站) 变更
        String oldDest = (existing != null && existing.getReality() != null)
                ? CollUtil.getFirst(existing.getReality().getServerNames()) : null;
        String newDest = StrUtil.trimToNull(input(newInput).getRealityDest());
        if (!ObjectUtil.equal(oldDest, newDest)) {
            diffs.add("realityDest: " + oldDest + " → " + newDest);
        }
        // reality x25519 密钥每次重装必重新生成 → 在用客户 pbk 失效, 恒为客户面变更
        diffs.add("reality 密钥 (重装重新生成, 客户端 pbk 失效, 必须重拉订阅)");
        return diffs;
    }

    @Override
    public String buildShareLink(InboundParams params, ShareContext ctx) {
        // reality 无对外域名, host 恒为线路机出网 IP
        String host = ctx.getServerIp();
        if (StrUtil.isBlank(host)) {
            return null;
        }
        VlessRealityParams p = (params instanceof VlessRealityParams v) ? v : null;
        RealityFields r = realityFields(p);
        StringBuilder query = new StringBuilder("encryption=none&security=reality");
        query.append("&type=").append(NETWORK_TCP);
        if (p != null && StrUtil.isNotBlank(p.getFlow())) {
            query.append("&flow=").append(p.getFlow());
        }
        if (StrUtil.isNotBlank(r.sni)) {
            query.append("&sni=").append(urlEncode(r.sni));
        }
        if (StrUtil.isNotBlank(r.fingerprint)) {
            query.append("&fp=").append(r.fingerprint);
        }
        if (StrUtil.isNotBlank(r.publicKey)) {
            query.append("&pbk=").append(urlEncode(r.publicKey));
        }
        if (StrUtil.isNotBlank(r.shortId)) {
            query.append("&sid=").append(r.shortId);
        }
        return "vless://" + ctx.getUuid() + "@" + host + ":" + ctx.getPort() + "?" + query
                + "#" + urlEncode(StrUtil.nullToEmpty(ctx.getLabel()));
    }

    @Override
    public Map<String, Object> buildClashProxy(InboundParams params, ShareContext ctx) {
        String host = ctx.getServerIp();
        if (StrUtil.isBlank(host)) {
            return null;
        }
        VlessRealityParams p = (params instanceof VlessRealityParams v) ? v : null;
        RealityFields r = realityFields(p);
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", ctx.getLabel());
        proxy.put("type", PROTOCOL_VLESS);
        proxy.put("server", host);
        proxy.put("port", ctx.getPort());
        proxy.put("uuid", ctx.getUuid());
        proxy.put("network", NETWORK_TCP);
        proxy.put("udp", true);
        proxy.put("tls", true);
        if (p != null && StrUtil.isNotBlank(p.getFlow())) {
            proxy.put("flow", p.getFlow());
        }
        if (StrUtil.isNotBlank(r.sni)) {
            proxy.put("servername", r.sni);
        }
        if (StrUtil.isNotBlank(r.fingerprint)) {
            proxy.put("client-fingerprint", r.fingerprint);
        }
        Map<String, Object> realityOpts = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(r.publicKey)) {
            realityOpts.put("public-key", r.publicKey);
        }
        if (StrUtil.isNotBlank(r.shortId)) {
            realityOpts.put("short-id", r.shortId);
        }
        proxy.put("reality-opts", realityOpts);
        return proxy;
    }

    @Override
    public String displayName() {
        return "VLESS + REALITY";
    }

    @Override
    public List<InboundFieldSchema> formSchema() {
        return List.of(
                InboundFieldSchema.builder().name("realityDest").label("REALITY 目标站").type("select").required(true)
                        .optionsKey("realityDest").allowCustom(true).placeholder("如 www.bing.com")
                        .pattern("^(?=.{1,253}$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))+$")
                        .build());
    }

    @Override
    public Map<String, Object> formPrefill(InboundParams params, String domainId, String subdomain) {
        VlessRealityParams p = (params instanceof VlessRealityParams v) ? v : null;
        Map<String, Object> values = new LinkedHashMap<>();
        String dest = (p != null && p.getReality() != null) ? CollUtil.getFirst(p.getReality().getServerNames()) : null;
        values.put("realityDest", dest);
        return values;
    }

    /** 从 params 取客户面 reality 字段 (sni/fp/pbk/sid); params 或 reality 缺则全 null. */
    private static RealityFields realityFields(VlessRealityParams p) {
        VlessRealityParams.RealityParams reality = (p != null) ? p.getReality() : null;
        if (reality == null) {
            return new RealityFields(null, null, null, null);
        }
        return new RealityFields(CollUtil.getFirst(reality.getServerNames()), reality.getFingerprint(),
                reality.getPublicKey(), firstShortId(reality.getShortIds()));
    }

    /** 客户面 reality 字段投影 (sni = serverNames 首个, sid = firstShortId). */
    private record RealityFields(String sni, String fingerprint, String publicKey, String shortId) {
    }

    /** URL 编码 (空格转 %20; vless 链接 query / fragment 用). */
    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** shortId 取首个非空, 都空取第一个; 订阅 sid 用. */
    private static String firstShortId(List<String> shortIds) {
        if (CollUtil.isEmpty(shortIds)) {
            return null;
        }
        for (String s : shortIds) {
            if (StrUtil.isNotBlank(s)) {
                return s;
            }
        }
        return shortIds.get(0);
    }
}
