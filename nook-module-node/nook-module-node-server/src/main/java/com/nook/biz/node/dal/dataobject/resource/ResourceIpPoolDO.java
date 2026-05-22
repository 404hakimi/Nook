package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceIpPoolLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** IP 池条目 (出口 IP + SOCKS5 凭据); lifecycleState 装机视角, status allocator 占用视角. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_ip_pool")
public class ResourceIpPoolDO extends BaseEntity {

    /** 区域码; FK → resource_region.code. */
    private String region;

    /** 关联 resource_ip_type.id. */
    private String ipTypeId;

    /** 装机生命周期; 取值见 {@link ResourceIpPoolLifecycleEnum}. */
    private String lifecycleState;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum}. */
    private Integer provisionMode;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址. */
    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;
    private String socks5Password;

    /** 占用状态; 取值见 {@link ResourceIpPoolStatusEnum}. */
    private String status;

    /** status=OCCUPIED 时填占用会员 id (旧字段 assignedMemberId 改名). */
    private String occupiedByMemberId;

    /** 占用时间 (旧字段 assignedAt 改名). */
    private LocalDateTime occupiedAt;

    /** status=COOLING 时填冷却结束时间. */
    private LocalDateTime coolingUntil;

    /** status=RESERVED 时填超时时间. */
    private LocalDateTime reservedExpiresAt;

    private Integer assignCount;
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
    private LocalDate expiresAt;

    private String remark;

    @TableLogic
    private Integer deleted;
}
