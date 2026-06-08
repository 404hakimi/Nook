package com.nook.biz.node.controller.resource.vo.landing;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - 落地机列表项 Response VO (主表 + landing / billing / quota / runtime 按需聚合)
 *
 * @author nook
 */
@Data
public class ServerLandingListItemRespVO {

    /** 落地节点编号. */
    private String id;
    /** 出网真实 IP. */
    private String ipAddress;
    /** 区域码. */
    private String region;
    /** 装机生命周期. */
    private String lifecycleState;

    /** IP 类型. */
    private String ipTypeId;
    /** 1=自部署 2=第三方. */
    private Integer provisionMode;
    /** dante 探测版本; null = 未装. */
    private String danteVersion;
    /** 装机完成时刻. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;
    /** SOCKS5 端口. */
    private Integer socks5Port;
    /** SOCKS5 用户名. */
    private String socks5Username;
    /** 明文密码; admin 后台展示. */
    private String socks5Password;

    /** 账单到期; null = 未设. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;

    /** 出站带宽上限 (Mbps). */
    private Integer bandwidthMbps;
    /** 总流量配额 (GB). */
    private Integer totalGb;

    /** agent 上报版本; null = 未上报. */
    private String agentVersion;
    /** 最近心跳时刻. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatAt;
    /** 在线状态 {@link com.nook.biz.agent.api.enums.AgentOnlineState}. */
    private String onlineState;
    /** 距上次心跳秒数; null = 从未上报. */
    private Long elapsedSec;
}
