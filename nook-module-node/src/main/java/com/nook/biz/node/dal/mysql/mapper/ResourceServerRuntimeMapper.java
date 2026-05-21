package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/** ResourceServerRuntime 数据访问. */
@Mapper
public interface ResourceServerRuntimeMapper extends BaseMapper<ResourceServerRuntimeDO> {

    /** 心跳到达; 更新 last_heartbeat_at + clear consecutive_miss. */
    default int onHeartbeat(String serverId, LocalDateTime at, String agentVersion, String agentSeenIp) {
        return update(null, Wrappers.<ResourceServerRuntimeDO>lambdaUpdate()
                .set(ResourceServerRuntimeDO::getLastHeartbeatAt, at)
                .set(ResourceServerRuntimeDO::getConsecutiveMiss, 0)
                .set(ResourceServerRuntimeDO::getTempUnhealthy, 0)
                .set(ResourceServerRuntimeDO::getAgentVersion, agentVersion)
                .set(ResourceServerRuntimeDO::getLastAgentSeenIp, agentSeenIp)
                .set(ResourceServerRuntimeDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerRuntimeDO::getServerId, serverId));
    }

    /** 心跳超时分级 (3-5min). */
    default int markTempUnhealthy(String serverId, Integer tempUnhealthy) {
        return update(null, Wrappers.<ResourceServerRuntimeDO>lambdaUpdate()
                .set(ResourceServerRuntimeDO::getTempUnhealthy, tempUnhealthy)
                .set(ResourceServerRuntimeDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerRuntimeDO::getServerId, serverId));
    }
}
