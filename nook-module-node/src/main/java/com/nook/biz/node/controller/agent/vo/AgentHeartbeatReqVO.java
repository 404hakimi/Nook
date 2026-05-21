package com.nook.biz.node.controller.agent.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Agent 心跳上报入参. */
@Data
public class AgentHeartbeatReqVO {

    /** Agent 二进制版本号. */
    @Size(max = 32)
    private String agentVersion;
}
