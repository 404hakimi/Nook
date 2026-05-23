package com.nook.biz.agent.service;

import com.nook.biz.agent.api.enums.AgentHostType;

/**
 * Agent 任务派发 Service 接口
 *
 * @author nook
 */
public interface AgentTaskDispatchService {

    /**
     * 派发任务到指定 host (线路机 或 落地机)
     *
     * @param hostType    主机类型
     * @param hostId      主机 id (resource_server.id 或 resource_ip_pool.id)
     * @param taskType    任务类型
     * @param payloadJson 任务参数 JSON
     * @return 任务编号
     */
    String dispatch(AgentHostType hostType, String hostId, String taskType, String payloadJson);
}
