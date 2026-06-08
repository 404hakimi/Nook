package com.nook.biz.node.controller.resource.vo.landing;

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

    /** 落地节点编号. */
    private String id;
    /** 别名. */
    private String name;

    /** 区域码. */
    private String region;

    /** IP 类型. */
    private String ipTypeId;

    /** 装机生命周期: 装机中 / 待上线 / 运行中 / 已退役. */
    private String lifecycleState;

    /** 出网真实 IP. */
    private String ipAddress;

    /** SOCKS5 端口. */
    private Integer socks5Port;
    /** SOCKS5 用户名. */
    private String socks5Username;

    /** 明文密码; admin 后台展示. */
    private String socks5Password;

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

    /** 装机完成时刻. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;

    /** dante 探测版本 (sockd -v); null = 未装. */
    private String danteVersion;

    /** agent 鉴权 token; 装机时一次性签发. */
    private String agentToken;

    /** 备注. */
    private String remark;

    // SSH 凭据从 /admin/resource/server/get-credential 单独获取, 不在此 VO

    // ===== 账面 (billing 子表) =====
    /** 月成本. */
    private BigDecimal costMonthly;
    /** 账单日. */
    private Integer billingCycleDay;

    /** 到期时间. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;

    // ===== quota 子表 =====
    /** 出站带宽上限 (Mbps). */
    private Integer bandwidthMbps;
    /** 总流量配额 (GB). */
    private Integer totalGb;
    /** 当周期机器已用字节 = rx + tx. */
    private Long usedBytes;
    /** 当周期入站字节. */
    private Long rxBytes;
    /** 当周期出站字节. */
    private Long txBytes;
    /** 重置策略: 按月 / 固定. */
    private String resetPolicy;
    /** 限流状态: 正常 / 已限流. */
    private String throttleState;

    // ===== runtime 子表 =====
    /** 最近心跳时刻. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatAt;

    /** agent 上报版本 (形如 landing-0.8.2); null = 未上报. */
    private String agentVersion;

    /** 在线状态 {@link com.nook.biz.agent.api.enums.AgentOnlineState} (与线路机同一判定). */
    private String onlineState;

    /** 距上次心跳秒数; null = 从未上报. */
    private Long elapsedSec;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
