package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - SOCKS5 落地节点 Response VO
 *
 * @author nook
 */
@Data
public class ServerLandingRespVO {

    private String id;
    private String name;

    /** 区域码. */
    private String region;

    /** IP 类型. */
    private String ipTypeId;

    /** 装机生命周期 (INSTALLING/READY/LIVE/RETIRED). */
    private String lifecycleState;

    /** 出网真实 IP. */
    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;

    /** 明文密码; admin 后台展示. */
    private String socks5Password;

    /** 占用状态 (AVAILABLE/RESERVED/OCCUPIED/COOLING). */
    private String status;

    private String occupiedByMemberId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime occupiedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime coolingUntil;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reservedExpiresAt;

    /** 累计被分配过几次. */
    private Integer assignCount;

    /** 1=自部署 2=第三方. */
    private Integer provisionMode;

    /** dante 日志关键字组合. */
    private String logLevel;

    /** dante logoutput 路径. */
    private String logPath;

    /** systemd 开机自启 1/0. */
    private Integer autostartEnabled;

    /** 部署时是否配 UFW 1/0. */
    private Integer firewallEnabled;

    /** SOCKS5 安装目录. */
    private String installDir;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;

    /** agent 鉴权 token; 装机时一次性签发. */
    private String agentToken;

    private String remark;

    // SSH 凭据从 /admin/resource/server/get-credential 单独获取, 不在此 VO

    // ===== 账面 (billing 子表) =====
    private BigDecimal costMonthlyUsd;
    private Integer billingCycleDay;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;

    // ===== capacity 子表 =====
    private Integer bandwidthLimitMbps;
    private Integer monthlyTrafficGb;
    private Long usedTrafficBytes;
    private Long rxBytes;
    private Long txBytes;
    private String quotaResetPolicy;
    private String throttleState;

    // ===== runtime 子表 =====
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
