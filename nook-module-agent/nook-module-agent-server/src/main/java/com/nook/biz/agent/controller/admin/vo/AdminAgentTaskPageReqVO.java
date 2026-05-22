package com.nook.biz.agent.controller.admin.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Admin 看 server task 历史 分页入参. */
@Data
public class AdminAgentTaskPageReqVO {

    @Min(1)
    private Integer pageNo = 1;

    @Min(1) @Max(100)
    private Integer pageSize = 20;

    /** 类型筛选; agent_upgrade / config_reload / truncate_log / xray_* / ping; 空=全部. */
    private String taskType;

    /** 状态筛选; PENDING / PICKED / SUCCESS / FAILED; 空=全部. */
    private String status;
}
