package com.nook.biz.agent.controller.vo;

import com.nook.biz.agent.api.enums.AgentTaskType;
import lombok.Data;

/**
 * Agent 任务 Response VO
 *
 * @author nook
 */
@Data
public class AgentTaskRespVO {

    /** 任务编号. */
    private String id;

    /** 任务类型 {@link AgentTaskType} */
    private String taskType;

    /** 任务参数 JSON; agent 端按任务类型反序列化. */
    private String taskPayload;
}
