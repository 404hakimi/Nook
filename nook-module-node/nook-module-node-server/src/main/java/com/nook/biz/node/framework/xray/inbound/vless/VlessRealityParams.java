package com.nook.biz.node.framework.xray.inbound.vless;

import com.nook.biz.node.framework.xray.inbound.InboundParams;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * vless + Vision + REALITY 语义参数; flow 流控 + reality 密钥/伪装
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class VlessRealityParams extends InboundParams {

    /** VLESS 流控; vless+reality 固定 xtls-rprx-vision. */
    private String flow;

    /** REALITY 安全层参数. */
    private RealityParams reality;

    /**
     * REALITY 安全层参数
     *
     * @author nook
     */
    @Data
    public static class RealityParams {

        /** 偷取的目标真站 host:port. */
        private String dest;

        /** 伪装的 SNI 列表; 与 dest 证书匹配. */
        private List<String> serverNames;

        /** x25519 私钥; 进服务端 realitySettings. */
        private String privateKey;

        /** x25519 公钥; 进客户端订阅 pbk. */
        private String publicKey;

        /** 允许的 shortId 列表; 含空串. */
        private List<String> shortIds;

        /** 客户端 uTLS 指纹; 进订阅 fp / Clash client-fingerprint. */
        private String fingerprint;
    }
}
