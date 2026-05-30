package com.nook.biz.agent.service;

import com.nook.biz.agent.api.enums.AgentRole;

/**
 * Agent 任务派发 Service 接口
 *
 * @author nook
 */
public interface AgentTaskDispatchService {

    /**
     * 派发任务给 agent
     *
     * @param agentType   agent 角色
     * @param sourceId    装机源 server 编号
     * @param taskType    任务类型
     * @param payloadJson 任务参数 JSON
     * @return 任务编号
     */
    String dispatch(AgentRole agentType, String sourceId, String taskType, String payloadJson);
}
