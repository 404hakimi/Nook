package com.nook.biz.resource.controller.ip.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IP 池详情/列表响应。
 * SOCKS5 密码不下发原文, 仅用 socks5PasswordConfigured 布尔标识是否已配置。
 */
@Data
public class ResourceIpPoolRespVO {

    private String id;
    private String region;
    private String ipTypeId;
    private String ipAddress;

    private String socks5Host;
    private Integer socks5Port;
    private String socks5Username;
    private Boolean socks5PasswordConfigured;

    /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded */
    private Integer status;

    private String assignedMemberId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime coolingUntil;

    private BigDecimal score;
    private Integer scamalyticsScore;
    private Integer ipqsScore;
    private Integer assignCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHealthAt;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
