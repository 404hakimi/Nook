package com.nook.biz.agent.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * Agent 运行时配置 Mapper
 *
 * @author nook
 */
@Mapper
public interface AgentRuntimeConfigMapper extends BaseMapper<AgentRuntimeConfigDO> {

    /** 更新 yaml; updatedAt / updatedBy 显式 set 不走 fill (PK 不是 BaseEntity.id). */
    default int updateConfigYaml(String serverId, String yaml, LocalDateTime updatedAt, String updatedBy) {
        return update(null, Wrappers.<AgentRuntimeConfigDO>lambdaUpdate()
                .set(AgentRuntimeConfigDO::getConfigYaml, yaml)
                .set(AgentRuntimeConfigDO::getUpdatedAt, updatedAt)
                .set(AgentRuntimeConfigDO::getUpdatedBy, updatedBy)
                .eq(AgentRuntimeConfigDO::getServerId, serverId));
    }

    /** 回写应用时间 + 应用 md5. */
    default int updateApplied(String serverId, LocalDateTime appliedAt, String appliedYamlMd5) {
        return update(null, Wrappers.<AgentRuntimeConfigDO>lambdaUpdate()
                .set(AgentRuntimeConfigDO::getAppliedAt, appliedAt)
                .set(AgentRuntimeConfigDO::getAppliedYamlMd5, appliedYamlMd5)
                .eq(AgentRuntimeConfigDO::getServerId, serverId));
    }
}
