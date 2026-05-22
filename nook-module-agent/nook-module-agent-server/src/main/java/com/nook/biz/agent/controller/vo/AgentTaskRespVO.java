package com.nook.biz.agent.controller.vo;

import lombok.Data;

/** 派给 agent 的单个任务 (GET /api/agent/tasks 返回列表). */
@Data
public class AgentTaskRespVO {

    private String id;

    private String taskType;

    /** 序列化的 JSON 字符串; agent 端按 task_type 反序列化. */
    private String taskPayload;
}
