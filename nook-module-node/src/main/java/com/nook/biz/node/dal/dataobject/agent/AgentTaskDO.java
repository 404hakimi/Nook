package com.nook.biz.node.dal.dataobject.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 任务队列条目 (backend 写, agent 轮询拉).
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task")
public class AgentTaskDO extends BaseEntity {

    /** FK → resource_server.id. */
    private String serverId;

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
