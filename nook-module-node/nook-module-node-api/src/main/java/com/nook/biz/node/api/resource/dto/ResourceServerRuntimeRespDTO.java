package com.nook.biz.node.api.resource.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Server 运行时状态 (心跳 / agent 版本 / 健康标志). */
@Data
public class ResourceServerRuntimeRespDTO {

    /** server 主键. */
    private String serverId;

    /** 上次心跳时间; null = 从未上报. */
    private LocalDateTime lastHeartbeatAt;

    /** Backend 主动标的临时不健康 (1=不健康). */
    private Integer tempUnhealthy;

    /** Agent 自报版本字符串 (e.g., "frontline-0.7.0"). */
    private String agentVersion;

    /** 上次 agent 发心跳的客户端 IP. */
    private String lastAgentSeenIp;

    /** 连续 miss 心跳次数. */
    private Integer consecutiveMiss;
}
