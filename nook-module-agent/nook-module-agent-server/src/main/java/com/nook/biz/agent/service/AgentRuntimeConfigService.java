package com.nook.biz.agent.service;

import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;

/**
 * Agent 运行时配置 Service 接口
 *
 * @author nook
 */
public interface AgentRuntimeConfigService {

    /**
     * 获得 Agent 运行时配置
     *
     * @param serverId server 编号
     * @return 配置对象 (未配置返 null)
     */
    AgentRuntimeConfigDO get(String serverId);

    /**
     * 保存 yaml 并派发配置重载任务
     *
     * @param serverId   server 编号
     * @param agentType  agent 角色
     * @param yaml       yaml 内容
     * @param operatorId 操作人编号
     * @return 任务编号
     */
    String save(String serverId, AgentRole agentType, String yaml, String operatorId);

    /**
     * 装机时把当前渲染的 yaml 直接入库标已同步
     *
     * @param serverId   server 编号
     * @param agentType  agent 角色
     * @param yaml       装机时渲染的完整 yaml
     * @param operatorId 操作人编号
     */
    void recordAsSynced(String serverId, AgentRole agentType, String yaml, String operatorId);

    /**
     * 配置重载成功回调
     *
     * @param serverId       server 编号
     * @param appliedYamlMd5 已应用 yaml 的 md5
     */
    void onConfigReloadSuccess(String serverId, String appliedYamlMd5);
}
