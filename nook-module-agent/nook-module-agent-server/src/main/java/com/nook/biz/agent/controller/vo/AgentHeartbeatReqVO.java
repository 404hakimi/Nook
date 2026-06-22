package com.nook.biz.agent.controller.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 心跳上报 Request VO
 *
 * @author nook
 */
@Data
public class AgentHeartbeatReqVO {

    /** Agent 二进制版本号. */
    @Size(max = 32)
    private String agentVersion;

    /** xray 当前是否运行 (frontline 报; 后台据此把卡死的 deploying 推进到 ok). null = 不报 (landing). */
    private Boolean xrayActive;
}
