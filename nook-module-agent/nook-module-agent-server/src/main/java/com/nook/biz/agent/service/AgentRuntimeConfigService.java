package com.nook.biz.agent.service;

import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;

/** Agent 运行时配置: admin 改整段 yaml → config_reload task 推给 agent. */
public interface AgentRuntimeConfigService {

    /** 取某 server 配置; 未配置返 null. */
    AgentRuntimeConfigDO get(String serverId);

    /**
     * Admin 保存 yaml 并派 config_reload task.
     *
     * @param yaml       完整 yaml; 仅做语法校验, 字段语义 agent 端处理
     * @param operatorId 操作 admin 的 id
     * @return 派发的 task id
     */
    String save(String serverId, String yaml, String operatorId);

    /** config_reload task SUCCESS 回调: 写 applied_at + applied_yaml_md5. */
    void onConfigReloadSuccess(String serverId, String appliedYamlMd5);
}
