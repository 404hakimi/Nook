package com.nook.biz.agent.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Agent 详情 Response VO
 *
 * @author nook
 */
@Data
public class AdminAgentDetailRespVO {

    private String serverId;
    private String serverName;
    private String host;
    private String lifecycleState;

    private String agentVersion;
    private String lastAgentSeenIp;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatAt;

    private Integer tempUnhealthy;
    private Integer consecutiveMiss;
    private Long elapsedSec;
    private String onlineState;

    /** agent_token 末 8 位 (脱敏). */
    private String agentTokenSuffix;
}
