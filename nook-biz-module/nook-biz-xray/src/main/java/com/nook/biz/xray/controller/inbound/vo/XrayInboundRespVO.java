package com.nook.biz.xray.controller.inbound.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class XrayInboundRespVO {

    private String id;
    private String serverId;
    private String ipId;
    private String memberUserId;
    private String backendType;
    private String externalInboundRef;
    private String protocol;
    private String transport;
    private String listenIp;
    private Integer listenPort;
    /** 协议密钥仅对 super_admin / 排查现场可见；普通运营列表里前端可隐藏 */
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
