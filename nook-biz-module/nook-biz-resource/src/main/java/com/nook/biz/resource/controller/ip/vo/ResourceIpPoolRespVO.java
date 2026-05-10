package com.nook.biz.resource.controller.ip.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 池详情/列表响应。
 * SOCKS5 密码以明文下发 — 数据库本就明文存储, 后台运营在受信网络下使用,
 * 编辑时直接 fill 进密码框 (type=password, UI 自然遮盖)。
 */
@Data
public class ResourceIpPoolRespVO {

    private String id;
    private String region;
    private String ipTypeId;

    /** 1=self_deploy 2=external */
    private Integer provisionMode;

    private String ipAddress;

    private String socks5Host;
    private Integer socks5Port;
    private String socks5Username;
    private String socks5Password;

    /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded */
    private Integer status;

    private String assignedMemberId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime coolingUntil;

    private Integer assignCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHealthAt;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
