package com.nook.biz.agent.controller.vo;

import com.nook.biz.agent.api.enums.AgentTaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 任务结果上报 Request VO
 *
 * @author nook
 */
@Data
public class AgentTaskResultReqVO {

    /** 任务编号. */
    @NotBlank(message = "taskId 不能为空")
    @Size(max = 32)
    private String taskId;

    /** 执行结果状态 {@link AgentTaskStatus} */
    @NotBlank
    @Pattern(regexp = "SUCCESS|FAILED", message = "status 须为 SUCCESS 或 FAILED")
    private String status;

    /** 结果 / 错误详情 JSON 字符串. */
    private String resultPayload;
}
