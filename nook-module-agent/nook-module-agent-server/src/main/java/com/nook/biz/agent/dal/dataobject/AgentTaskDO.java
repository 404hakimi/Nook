package com.nook.biz.agent.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
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

    /** 任务目标主机类型; 取值见 {@link com.nook.biz.agent.api.enums.AgentHostType}. */
    private String hostType;

    /** resource_server.id (hostType=SERVER) 或 resource_ip_pool.id (hostType=IP_POOL). */
    private String hostId;

    /** 任务类型: xray_provision_user / xray_remove_user / shell_exec 等. */
    private String taskType;

    /** 任务参数 JSON (序列化为字符串落库; agent 端反序列化按 task_type 决定 schema). */
    private String taskPayload;

    /** 任务状态: PENDING / PICKED / SUCCESS / FAILED. */
    private String status;

    /** 被 agent 拉走的时间. */
    private LocalDateTime pickedAt;

    /** agent 回报结果 JSON. */
    private String resultPayload;

    /** 失败重试次数; 上限 3, 超限标 FAILED 永久. */
    private Integer retryCount;
}
