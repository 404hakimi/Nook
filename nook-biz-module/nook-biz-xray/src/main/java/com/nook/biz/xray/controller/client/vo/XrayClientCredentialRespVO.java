package com.nook.biz.xray.controller.client.vo;

import lombok.Data;

/**
 * Client 协议级凭据 (明文 UUID / password) 出参; 仅供"分享给会员"等需要明文的场景按需拉取。
 *
 * <p>与列表 RespVO 区分: 列表 / 详情接口 mask UUID (规范 §11), 这个接口走专用的 reveal 路径,
 * 调用方明确表示需要明文。
 */
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
