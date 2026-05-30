package com.nook.biz.agent.controller.admin.vo;

import com.nook.biz.agent.api.enums.AgentTaskStatus;
import com.nook.biz.agent.api.enums.AgentTaskType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台 - Agent 任务分页 Request VO
 *
 * @author nook
 */
@Data
public class AdminAgentTaskPageReqVO {

    /** 页码, 从 1 起. */
    @Min(1)
    private Integer pageNo = 1;

    /** 每页条数. */
    @Min(1) @Max(100)
    private Integer pageSize = 20;

    /** 任务类型筛选 {@link AgentTaskType}; 空=全部. */
    private String taskType;

    /** 任务状态筛选 {@link AgentTaskStatus}; 空=全部. */
    private String status;
}
