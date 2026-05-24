package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Xray inbound 共享配置 Resp VO (业务可热改)
 *
 * @author nook
 */
@Data
public class XrayConfigRespVO {

    private String serverId;

    /** 共享 inbound 协议 (vmess/trojan/...) */
    private String protocol;

    /** 共享 inbound 传输 (ws/tcp/...) */
    private String transport;

    /** 共享 inbound 监听 IP. */
    private String listenIp;

    /** 共享 inbound 监听端口. */
    private Integer sharedInboundPort;

    /** WebSocket transport path. */
    private String wsPath;

    /** 对外域名 (CDN CNAME 指向). */
    private String domain;

    /** TLS 证书路径. */
    private String tlsCertPath;

    /** TLS 私钥路径. */
    private String tlsKeyPath;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
