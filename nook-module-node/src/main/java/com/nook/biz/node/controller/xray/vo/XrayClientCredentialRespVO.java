package com.nook.biz.node.controller.xray.vo;

import lombok.Data;

/** Client 协议级凭据明文出参; 仅"分享给会员"等场景按需拉, 列表/详情接口 mask UUID. */
@Data
public class XrayClientCredentialRespVO {

    private String id;
    /** 协议级密钥明文; vless/vmess UUID, trojan password */
    private String clientUuid;
    private String clientEmail;
    private String protocol;
    /** 客户端连接的 host (resource_server.host); 拼订阅链接需要 */
    private String serverHost;
    private Integer listenPort;
    private String transport;
}
