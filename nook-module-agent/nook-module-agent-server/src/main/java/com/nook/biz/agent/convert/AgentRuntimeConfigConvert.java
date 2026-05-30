package com.nook.biz.agent.convert;

import com.nook.biz.agent.api.enums.AgentConfigSyncState;
import com.nook.biz.agent.controller.admin.vo.AgentRuntimeConfigRespVO;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AgentRuntimeConfigConvert {

    AgentRuntimeConfigConvert INSTANCE = Mappers.getMapper(AgentRuntimeConfigConvert.class);

    default AgentRuntimeConfigRespVO convertDetail(String serverId, AgentRuntimeConfigDO row,
                                                   AgentConfigSyncState syncState) {
        AgentRuntimeConfigRespVO vo = new AgentRuntimeConfigRespVO();
        vo.setServerId(serverId);
        vo.setSyncState(syncState.name());
        if (row == null) {
            return vo;
        }
        vo.setConfigYaml(row.getConfigYaml());
        vo.setUpdatedAt(row.getUpdatedAt());
        vo.setUpdatedBy(row.getUpdatedBy());
        vo.setAppliedAt(row.getAppliedAt());
        vo.setAppliedYamlMd5(row.getAppliedYamlMd5());
        return vo;
    }
}
