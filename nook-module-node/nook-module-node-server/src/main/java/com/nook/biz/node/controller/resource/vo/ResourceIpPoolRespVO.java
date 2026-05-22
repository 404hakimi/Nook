package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.node.api.enums.ResourceIpPoolLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IP 池列表 / 详情 Response VO.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolRespVO {

    private String id;

    /** 区域码 (FK → resource_region.code). */
    private String region;

    private String ipTypeId;

    /** 装机生命周期; 取值见 {@link ResourceIpPoolLifecycleEnum}. */
    private String lifecycleState;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum}. */
    private Integer provisionMode;

    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;

    /** 明文 SOCKS5 密码; 后台运营受信网络使用, UI 用 type=password 自然遮盖. */
    private String socks5Password;

    /** 占用状态; 取值见 {@link ResourceIpPoolStatusEnum}. */
    private String status;

    /** status=OCCUPIED 时填占用会员 id. */
    private String occupiedByMemberId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime occupiedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime coolingUntil;

    /** status=RESERVED 时填超时时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reservedExpiresAt;

    private Integer assignCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHealthAt;

    private String logLevel;

    private String logPath;

    private Integer autostartEnabled;

    private Integer firewallEnabled;

    private String firewallAllowFrom;

    private String installDir;

    private String sshHost;

    private Integer sshPort;

    private String sshUser;

    private String sshPassword;

    private Integer bandwidthMbps;

    private Integer trafficQuotaGb;

    /** 月度成本 USD. */
    private BigDecimal costMonthlyUsd;

    /** 账单日 (1-28). */
    private Integer billingCycleDay;

    /** IP 到期日. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
