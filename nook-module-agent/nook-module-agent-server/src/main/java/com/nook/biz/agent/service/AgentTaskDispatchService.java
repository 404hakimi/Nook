package com.nook.biz.agent.service;

/** Backend → Agent 任务派发: INSERT PENDING 行到 agent_task, agent 轮询拉取执行. */
public interface AgentTaskDispatchService {

    /**
     * 派任务到指定 server.
     *
     * @param taskType    任务类型 (agent_upgrade / config_reload / truncate_log / xray_* 等; agent 端按类型反序列化)
     * @param payloadJson 任务参数 JSON
     * @return 派出去的 task id
     */
    String dispatch(String serverId, String taskType, String payloadJson);
}
