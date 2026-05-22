package com.nook.biz.node.controller.xray.vo;

import lombok.Data;

/**
 * 管理后台 - Xray 客户端凭据 Response VO
 *
 * @author nook
 */
@Data
public class XrayClientCredentialRespVO {

    private String id;
    /** 协议级密钥明文; vless/vmess UUID, trojan password */
    private String clientUuid;
    private String clientEmail;
    private String protocol;
    /** 客户端连接的 host; domain 不空时下发 domain, 否则下发 server.host */
    private String serverHost;
    private Integer listenPort;
    private String transport;
    /** WS path. */
    private String wsPath;
    /** TLS 启用标志; true 时客户端 URI 加 security=tls + sni. */
    private Boolean tlsEnabled;
    /** TLS SNI (= node.domain). */
    private String sni;
}
