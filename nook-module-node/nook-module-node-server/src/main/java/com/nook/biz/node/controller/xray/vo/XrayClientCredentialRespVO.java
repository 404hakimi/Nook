package com.nook.biz.node.controller.xray.vo;

import lombok.Data;

/**
 * 管理后台 - Xray 客户端凭据 Response VO
 *
 * @author nook
 */
@Data
public class XrayClientCredentialRespVO {

    /** 客户端编号. */
    private String id;
    /** 协议级密钥明文; vless/vmess UUID, trojan password */
    private String clientUuid;
    /** 客户端标识 (email). */
    private String clientEmail;
    /** 协议. */
    private String protocol;
    /** 客户端连接的 host; domain 不空时下发 domain, 否则下发 server.host */
    private String serverHost;
    /** 监听端口. */
    private Integer listenPort;
    /** 传输方式. */
    private String transport;
    /** WS path. */
    private String wsPath;
    /** TLS 启用标志; true 时客户端 URI 加 security=tls + sni. */
    private Boolean tlsEnabled;
    /** TLS SNI (= node.domain). */
    private String sni;
}
