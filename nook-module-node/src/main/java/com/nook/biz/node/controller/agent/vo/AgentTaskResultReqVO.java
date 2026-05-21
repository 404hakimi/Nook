package com.nook.biz.node.controller.agent.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Agent 上报任务执行结果. */
@Data
public class AgentTaskResultReqVO {

    @NotBlank(message = "taskId 不能为空")
    @Size(max = 32)
    private String taskId;

    /** SUCCESS / FAILED. */
    @NotBlank
    @Pattern(regexp = "SUCCESS|FAILED", message = "status 须为 SUCCESS 或 FAILED")
    private String status;

    /** 结果 / 错误详情 JSON 字符串. */
    private String resultPayload;
}
