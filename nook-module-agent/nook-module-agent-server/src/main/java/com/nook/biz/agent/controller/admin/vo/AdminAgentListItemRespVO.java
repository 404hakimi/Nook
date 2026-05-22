package com.nook.biz.agent.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Agent 列表项 Response VO
 *
 * @author nook
 */
@Data
public class AdminAgentListItemRespVO {

    private String serverId;
    private String serverName;
    private String host;

    /** INSTALLING / READY / LIVE / RETIRED. */
    private String lifecycleState;

    /** agent 上报的版本号; null = 从未上报心跳 (装机未完成). */
    private String agentVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatAt;

    /** 0=健康 1=暂时不健康 (3-5min 心跳缺失). */
    private Integer tempUnhealthy;

    /** 距上次心跳秒数; null = 从未心跳. */
    private Long elapsedSec;

    /** agent 在线状态: ONLINE / WARN / TEMP_UNHEALTHY / OFFLINE / NEVER. */
    private String onlineState;

    /**
     * 运行时配置同步状态: NEVER_CONFIGURED (没改过) / SYNCED (agent 已应用最新) / PENDING (admin 改了 agent 还没应用).
     */
    private String configSyncState;
}
