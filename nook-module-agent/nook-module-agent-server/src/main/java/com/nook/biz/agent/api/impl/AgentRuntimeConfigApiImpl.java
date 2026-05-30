package com.nook.biz.agent.api.impl;

import com.nook.biz.agent.api.AgentRuntimeConfigApi;
import com.nook.biz.agent.api.enums.AgentConfigSyncState;
import com.nook.biz.agent.convert.AgentRuntimeConfigConvert;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentRuntimeConfigMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行时配置 Api 实现类
 *
 * @author nook
 */
@Service
public class AgentRuntimeConfigApiImpl implements AgentRuntimeConfigApi {

    @Resource
    private AgentRuntimeConfigMapper agentRuntimeConfigMapper;

    @Override
    public Map<String, String> getSyncStateMap(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) return Map.of();
        List<AgentRuntimeConfigDO> rows = agentRuntimeConfigMapper.selectBatchIds(serverIds);
        Map<String, String> result = new HashMap<>(serverIds.size());
        Map<String, AgentRuntimeConfigDO> rowMap = CollectionUtils.convertMap(
                rows, AgentRuntimeConfigDO::getServerId);
        for (String id : serverIds) {
            AgentRuntimeConfigDO row = rowMap.get(id);
            AgentConfigSyncState state = AgentRuntimeConfigConvert.INSTANCE.classifySyncState(row);
            result.put(id, state.name());
        }
        return result;
    }
}
