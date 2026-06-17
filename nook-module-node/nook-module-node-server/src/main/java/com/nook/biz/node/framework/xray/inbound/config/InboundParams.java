package com.nook.biz.node.framework.xray.inbound.config;

import lombok.Data;

import java.util.List;

/**
 * Xray inbound 协议/传输/安全细节语义参数 (对应 xray_inbound.params JSON 列)
 *
 * @author nook
 */
@Data
public class InboundParams {

    /** WebSocket 传输参数; 非 ws 传输留空. */
    private WsParams ws;

    /** TLS 安全层参数; security=tls 时有值. */
    private TlsParams tls;

    /** VLESS 流控; vless+reality 固定 xtls-rprx-vision, 其它协议留空. */
    private String flow;

    /** REALITY 安全层参数; security=reality 时有值. */
    private RealityParams reality;

    /**
     * WebSocket 传输参数
     *
     * @author nook
     */
    @Data
    public static class WsParams {

        /** WebSocket 接入路径. */
        private String path;
    }

    /**
     * TLS 安全层参数 (文件证书)
     *
     * @author nook
     */
    @Data
    public static class TlsParams {

        /** TLS 证书路径. */
        private String certPath;

        /** TLS 私钥路径. */
        private String keyPath;
    }

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
