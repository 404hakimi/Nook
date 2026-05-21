package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/** ResourceServerCapacity 数据访问. */
@Mapper
public interface ResourceServerCapacityMapper extends BaseMapper<ResourceServerCapacityDO> {

    /** 更新 NIC 已用流量 (Agent 上报字节数); 显式 set updated_at. */
    default int updateUsedTrafficBytes(String serverId, Long usedBytes) {
        return update(null, Wrappers.<ResourceServerCapacityDO>lambdaUpdate()
                .set(ResourceServerCapacityDO::getUsedTrafficBytes, usedBytes)
                .set(ResourceServerCapacityDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerCapacityDO::getServerId, serverId));
    }

    /** 更新 throttle_state; ServerCapacityStateJob 算完后调用. */
    default int updateThrottleState(String serverId, String throttleState) {
        return update(null, Wrappers.<ResourceServerCapacityDO>lambdaUpdate()
                .set(ResourceServerCapacityDO::getThrottleState, throttleState)
                .set(ResourceServerCapacityDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerCapacityDO::getServerId, serverId));
    }
}
