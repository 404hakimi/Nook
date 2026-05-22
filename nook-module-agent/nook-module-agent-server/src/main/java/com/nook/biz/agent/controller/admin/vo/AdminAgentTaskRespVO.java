package com.nook.biz.agent.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Agent 任务 Response VO
 *
 * @author nook
 */
@Data
public class AdminAgentTaskRespVO {

    private String id;

    /** agent_upgrade / config_reload / truncate_log / xray_* / ping. */
    private String taskType;

    /** PENDING / PICKED / SUCCESS / FAILED. */
    private String status;

    /** 派发参数 JSON 字符串. */
    private String taskPayload;

    /** Agent 上报结果 JSON 字符串; null = 还没 ack. */
    private String resultPayload;

    private Integer retryCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pickedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
