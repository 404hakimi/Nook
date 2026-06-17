package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/**
 * Xray 接入连接参数 DTO
 *
 * @author nook
 */
@Data
public class XrayInboundDTO {

    /** 连接 host: 线路机域名, 无则线路机出网 IP. */
    private String host;

    /** 共享 inbound 端口. */
    private Integer port;

    /** inbound 协议 (vmess/vless). */
    private String protocol;

    /** 传输 (ws/tcp/...). */
    private String transport;

    /** 安全层 (none/tls/reality). */
    private String security;

    /** WebSocket path (ws 传输). */
    private String wsPath;

    /** 是否启用 TLS (vmess+tls 用). */
    private boolean tls;

    /** VLESS 流控 (reality 用 xtls-rprx-vision). */
    private String flow;

    /** REALITY 公钥 (订阅 pbk). */
    private String publicKey;

    /** REALITY shortId (订阅 sid). */
    private String shortId;

    /** REALITY 伪装 SNI (订阅 sni). */
    private String serverName;

    /** REALITY uTLS 指纹 (订阅 fp). */
    private String fingerprint;
}
