package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.node.enums.ResourceServerLifecycleEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 服务器列表 / 详情 Response VO.
 *
 * @author nook
 */
@Data
public class ResourceServerRespVO {

    private String id;
    private String name;
    private String host;
    private Integer sshPort;
    private String sshUser;

    /** 明文 SSH 密码; 后台运营受信网络下使用, UI 用 type=password 自然遮盖. */
    private String sshPassword;

    private Integer sshTimeoutSeconds;
    private Integer sshOpTimeoutSeconds;
    private Integer sshUploadTimeoutSeconds;
    private Integer installTimeoutSeconds;

    /** 运营商承诺峰值带宽 (Mbps); 仅账面展示. */
    private Integer bandwidthMbps;

    /** 线路机域名 (e.g., jp-01.nook.com); LIVE 前置必填. */
    private String domain;

    /** Cloudflare Zone ID. */
    private String cfZoneId;

    /** Cloudflare DNS record ID. */
    private String cfRecordId;

    /** 月度成本 USD. */
    private BigDecimal costMonthlyUsd;

    /** 账单日 (1-28). */
    private Integer billingCycleDay;

    /** 服务器到期日. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;

    /** allocator 硬上限. */
    private Integer maxConcurrentClients;

    /** 装机生命周期; 取值见 {@link ResourceServerLifecycleEnum}. */
    private String lifecycleState;

    private Integer totalIpCount;
    private String idcProvider;

    /** 区域码 (FK → resource_region.code). */
    private String region;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
