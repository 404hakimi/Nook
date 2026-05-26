package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Xray 客户端 Response VO
 *
 * @author nook
 */
@Data
public class XrayClientRespVO {

    private String id;
    private String serverId;
    /** server 别名 (resource_server.name); 由 controller 预拉 + convert 填; server 已删该字段为空, 由前端 fallback 到 serverId. */
    private String serverName;
    /** server 主机地址 (resource_server.host); enrich 来源同 serverName. */
    private String serverHost;
    private String ipId;
    /** 落地节点 IP (resource_server.ip_address); 列表 enrich 后填; 落地缺失时为空. */
    private String ipAddress;
    private String memberUserId;
    private String protocol;
    private String transport;
    private String listenIp;
    private Integer listenPort;
    /**
     * 协议密钥
     */
    private String clientUuid;
    private String clientEmail;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
