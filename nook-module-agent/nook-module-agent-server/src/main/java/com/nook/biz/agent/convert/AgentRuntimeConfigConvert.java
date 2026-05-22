package com.nook.biz.agent.convert;

import com.nook.biz.agent.api.enums.AgentConfigSyncState;
import com.nook.biz.agent.controller.admin.vo.AgentRuntimeConfigRespVO;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * Agent 运行时配置 Convert
 *
 * @author nook
 */
@Mapper
public interface AgentRuntimeConfigConvert {

    AgentRuntimeConfigConvert INSTANCE = Mappers.getMapper(AgentRuntimeConfigConvert.class);

    /** 算 stored yaml 跟 applied md5 是否一致; row=null 视为从未配置. */
    default AgentConfigSyncState classifySyncState(AgentRuntimeConfigDO row) {
        if (row == null) return AgentConfigSyncState.NEVER_CONFIGURED;
        String storedMd5 = DigestUtils.md5DigestAsHex(
                (row.getConfigYaml() == null ? "" : row.getConfigYaml()).getBytes(StandardCharsets.UTF_8));
        return storedMd5.equals(row.getAppliedYamlMd5())
                ? AgentConfigSyncState.SYNCED
                : AgentConfigSyncState.PENDING;
    }

    /** 构建 admin 端详情 VO; row=null 仅填 serverId + NEVER_CONFIGURED. */
    default AgentRuntimeConfigRespVO convertDetail(String serverId, AgentRuntimeConfigDO row) {
        AgentRuntimeConfigRespVO vo = new AgentRuntimeConfigRespVO();
        vo.setServerId(serverId);
        if (row == null) {
            vo.setSyncState(AgentConfigSyncState.NEVER_CONFIGURED.name());
            return vo;
        }
        vo.setConfigYaml(row.getConfigYaml());
        vo.setUpdatedAt(row.getUpdatedAt());
        vo.setUpdatedBy(row.getUpdatedBy());
        vo.setAppliedAt(row.getAppliedAt());
        vo.setAppliedYamlMd5(row.getAppliedYamlMd5());
        vo.setSyncState(classifySyncState(row).name());
        return vo;
    }
}
