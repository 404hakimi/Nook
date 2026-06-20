package com.nook.biz.node.framework.xray.inbound.vless;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.controller.xray.vo.XrayInboundConfigVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.InboundTemplateRenderer;
import com.nook.biz.node.framework.xray.inbound.InboundProtocol;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionResult;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionRequest;
import com.nook.biz.node.framework.xray.inbound.InboundUserRequest;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    /** shortId 字节数 (hex 长度 = 2×). */
    private static final int SHORTID_BYTES = 8;
    /** dest 固定 443 端口 (TLS 站). */
    private static final int REALITY_DEST_PORT = 443;
    /** 偷取目标主机名格式 (FQDN, 不含端口); 预设候选与自定义输入统一按此校验. */
    private static final Pattern HOST_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))+$");
    /** vless+reality inbound 模板 (${} 占位符 render 时填充). */
    private static final String INBOUND_TEMPLATE = """
            {"tag":${tag},"listen":${listenIp},"port":${port},"protocol":"vless","settings":{"clients":[],"decryption":"none"},"sniffing":{"enabled":true,"destOverride":["http","tls","quic"]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"show":false,"dest":${reality.dest},"xver":0,"serverNames":${reality.serverNames},"privateKey":${reality.privateKey},"shortIds":${reality.shortIds}}}}""";

    @Resource
    private VlessRealityKeyGenerator vlessRealityKeyGenerator;
    @Resource
    private InboundTemplateRenderer inboundTemplateRenderer;

    @Override
    public boolean supports(String protocol) {
        return "vless".equalsIgnoreCase(protocol);
    }

    @Override
    public void validate(String serverId, XrayInstallReqVO reqVO) {
        // realityDest = 偷取目标真站主机名 (预设候选或自定义输入); 仅校验主机名格式, 不强制是预设之一
        String dest = StrUtil.trimToNull(reqVO.getInbound().getRealityDest());
        if (dest == null || !HOST_PATTERN.matcher(dest).matches()) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID,
                    "reality 装机 realityDest 必填且须是合法主机名 (如 www.bing.com)");
        }
    }

    @Override
    public InboundProvisionResult provision(InboundProvisionRequest ctx) {
        XrayInboundConfigVO inbound = ctx.getReqVO().getInbound();
        String serverName = inbound.getRealityDest().trim();
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
        vars.put("listenIp", inbound.getListenIp());
        vars.put("port", inbound.getSharedInboundPort());
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
        if (StrUtil.isNotBlank(user.getFlow())) {
            client.put("flow", user.getFlow());
        }
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
}
