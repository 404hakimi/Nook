package com.nook.biz.node.framework.xray.inbound.strategy;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.RealityDestPreset;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;
import com.nook.biz.node.framework.xray.inbound.config.RealityKeyGenerator;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * vless + Vision + REALITY 协议策略; 装机生成 x25519 密钥, dest 取自候选
 *
 * @author nook
 */
@Component
public class VlessRealityStrategy implements InboundProtocolStrategy {

    /** 固定流控 + uTLS 指纹. */
    private static final String FLOW_VISION = "xtls-rprx-vision";
    private static final String FINGERPRINT_CHROME = "chrome";
    /** shortId 字节数 (hex 长度 = 2×). */
    private static final int SHORTID_BYTES = 8;

    @Resource
    private RealityKeyGenerator realityKeyGenerator;

    @Override
    public boolean supports(XrayInstallReqVO reqVO) {
        return "vless".equalsIgnoreCase(reqVO.getProtocol());
    }

    @Override
    public void validate(String serverId, XrayInstallReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getRealityDest()) || RealityDestPreset.fromName(reqVO.getRealityDest()) == null) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID,
                    "reality 装机 realityDest 必填且须是有效候选");
        }
    }

    @Override
    public InboundProvision provision(XrayInstallReqVO reqVO, String fullDomain) {
        RealityDestPreset preset = RealityDestPreset.fromName(reqVO.getRealityDest());
        RealityKeyGenerator.RealityKeyPair keyPair = realityKeyGenerator.generateKeyPair();
        // 语义参数: reality 密钥 + dest (privateKey 进服务端, publicKey 进订阅)
        InboundParams params = new InboundParams();
        params.setFlow(FLOW_VISION);
        InboundParams.RealityParams reality = new InboundParams.RealityParams();
        reality.setDest(preset.getDest());
        reality.setServerNames(List.of(preset.getServerName()));
        reality.setPrivateKey(keyPair.privateKey());
        reality.setPublicKey(keyPair.publicKey());
        reality.setShortIds(List.of("", realityKeyGenerator.generateShortId(SHORTID_BYTES)));
        reality.setFingerprint(FINGERPRINT_CHROME);
        params.setReality(reality);
        // 模板占位符
        Map<String, Object> vars = new HashMap<>();
        vars.put("tag", XrayConstants.SHARED_INBOUND_TAG);
        vars.put("listenIp", reqVO.getListenIp());
        vars.put("port", reqVO.getSharedInboundPort());
        vars.put("reality.dest", reality.getDest());
        vars.put("reality.serverNames", reality.getServerNames());
        vars.put("reality.privateKey", reality.getPrivateKey());
        vars.put("reality.shortIds", reality.getShortIds());
        return new InboundProvision(XrayInboundProtocolEnum.VLESS_REALITY, params, vars);
    }
}
