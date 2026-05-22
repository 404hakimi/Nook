package com.nook.biz.agent.controller.vo;

import lombok.Data;

/**
 * Agent 任务 Response VO
 *
 * @author nook
 */
@Data
public class AgentTaskRespVO {

    private String id;

    private String taskType;

    /** 序列化的 JSON 字符串; agent 端按 task_type 反序列化. */
    private String taskPayload;
}
