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

    /** inbound 协议 (vmess/...). */
    private String protocol;

    /** 传输 (ws/tcp/...). */
    private String transport;

    /** WebSocket path. */
    private String wsPath;

    /** 是否启用 TLS. */
    private boolean tls;
}
