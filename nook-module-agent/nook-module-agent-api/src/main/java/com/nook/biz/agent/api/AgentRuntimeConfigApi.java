package com.nook.biz.agent.api;

import java.util.Collection;
import java.util.Map;

/**
 * Agent 运行时配置跨模块 Api (供 node 模块查 syncState 拼 server 列表用)
 *
 * @author nook
 */
public interface AgentRuntimeConfigApi {

    /**
     * 批量取 server 的 config sync state.
     *
     * @param serverIds server id 集合
     * @return serverId → syncState (NEVER_CONFIGURED / SYNCED / PENDING)
     */
    Map<String, String> getSyncStateMap(Collection<String> serverIds);
}
