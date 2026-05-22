package com.nook.biz.agent.service;

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
     * 保存 yaml 并派发 config_reload 任务
     *
     * @param serverId   server 编号
     * @param yaml       yaml 内容
     * @param operatorId 操作人编号
     * @return 任务编号
     */
    String save(String serverId, String yaml, String operatorId);

    /**
     * config_reload 成功回调: 回写 applied 时间与 md5
     *
     * @param serverId       server 编号
     * @param appliedYamlMd5 已应用 yaml 的 md5
     */
    void onConfigReloadSuccess(String serverId, String appliedYamlMd5);
}
