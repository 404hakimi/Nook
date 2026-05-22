package com.nook.biz.agent.service;

/**
 * Agent 任务派发 Service 接口
 *
 * @author nook
 */
public interface AgentTaskDispatchService {

    /**
     * 派发任务到指定 server
     *
     * @param serverId    server 编号
     * @param taskType    任务类型
     * @param payloadJson 任务参数 JSON
     * @return 任务编号
     */
    String dispatch(String serverId, String taskType, String payloadJson);
}
