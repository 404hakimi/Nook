package com.nook.biz.agent.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.api.enums.AgentTaskStatus;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 任务 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task")
public class AgentTaskDO extends BaseEntity {

    /** agent 角色 {@link AgentRole} */
    private String agentType;

    /** 装机源服务器 id. */
    private String sourceId;

    /** 任务类型 {@link AgentTaskType} */
    private String taskType;

    /** 任务参数 JSON (序列化为字符串落库; agent 端反序列化按 task_type 决定 schema). */
    private String taskPayload;

    /** 任务状态 {@link AgentTaskStatus} */
    private String status;

    /** 被 agent 拉走的时间. */
    private LocalDateTime pickedAt;

    /** agent 回报结果 JSON. */
    private String resultPayload;

    /** 失败重试次数; 上限 3, 超限标 FAILED 永久. */
    private Integer retryCount;
}
