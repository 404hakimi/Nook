package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/**
 * Xray 客户端节点连接信息 (订阅 URL 拼 vmess 用; 跨模块契约).
 *
 * @author nook
 */
@Data
public class XrayClientNodeDTO {

    /** xray_client.id (映射回订阅记录). */
    private String clientId;

    /** 协议级 UUID (vmess id). */
    private String clientUuid;

    /** 连接 host: 线路机 xray_config.domain, 无则线路机出网 IP. */
    private String host;

    /** 共享 inbound 端口. */
    private Integer port;

    /** inbound 协议 (vmess/...). */
    private String protocol;

    /** 传输 (ws/tcp/...). */
    private String transport;

    /** WebSocket path. */
    private String wsPath;

    /** 是否启用 TLS (tlsCertPath 非空). */
    private boolean tls;
}
