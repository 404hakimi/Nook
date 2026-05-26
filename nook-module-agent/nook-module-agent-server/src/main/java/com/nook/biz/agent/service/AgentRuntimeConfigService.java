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
     * Admin 热改 yaml: 保存并派发 config_reload 任务 (agent 拉到后会重启自身应用新 yaml).
     *
     * @param serverId   server 编号
     * @param agentType  agent 角色 (frontline / landing)
     * @param yaml       yaml 内容
     * @param operatorId 操作人编号
     * @return 任务编号
     */
    String save(String serverId, AgentRole agentType, String yaml, String operatorId);

    /**
     * 装机时把当前渲染的 yaml 直接入库标 SYNCED (装机已经把 yaml 写到远端 file, 不需要再派 CONFIG_RELOAD task).
     *
     * <p>跟 {@link #save} 区别: 不派 task; applied_md5 直接等于 yaml 的 md5, applied_at = now;
     * 配合"先入库后落地" 设计: SSH 失败时 DB 已有数据, 可按 DB 重做装机.
     *
     * @param serverId   server 编号
     * @param agentType  agent 角色
     * @param yaml       装机时渲染的完整 yaml
     * @param operatorId 操作人编号 (装机走 backend 自动调时填 "system-install")
     */
    void recordAsSynced(String serverId, AgentRole agentType, String yaml, String operatorId);

    /**
     * config_reload 成功回调: 回写 applied 时间与 md5
     *
     * @param serverId       server 编号
     * @param appliedYamlMd5 已应用 yaml 的 md5
     */
    void onConfigReloadSuccess(String serverId, String appliedYamlMd5);
}
