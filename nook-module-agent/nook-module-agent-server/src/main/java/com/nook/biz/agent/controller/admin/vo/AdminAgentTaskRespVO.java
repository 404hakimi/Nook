package com.nook.biz.agent.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.agent.api.enums.AgentTaskStatus;
import com.nook.biz.agent.api.enums.AgentTaskType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Agent 任务 Response VO
 *
 * @author nook
 */
@Data
public class AdminAgentTaskRespVO {

    /** 任务编号. */
    private String id;

    /** 任务类型 {@link AgentTaskType} */
    private String taskType;

    /** 任务状态 {@link AgentTaskStatus} */
    private String status;

    /** 派发参数 JSON 字符串. */
    private String taskPayload;

    /** Agent 上报结果 JSON 字符串; null = 还没 ack. */
    private String resultPayload;

    /** 重试次数. */
    private Integer retryCount;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** Agent 拾取时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pickedAt;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
