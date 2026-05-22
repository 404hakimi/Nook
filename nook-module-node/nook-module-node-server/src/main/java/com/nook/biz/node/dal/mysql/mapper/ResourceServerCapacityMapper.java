package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 资源服务器容量 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerCapacityMapper extends BaseMapper<ResourceServerCapacityDO> {

    /** 更新 NIC 已用流量 (Agent 上报字节数); 显式 set updated_at. */
    default int updateUsedTrafficBytes(String serverId, Long usedBytes) {
        return update(null, Wrappers.<ResourceServerCapacityDO>lambdaUpdate()
                .set(ResourceServerCapacityDO::getUsedTrafficBytes, usedBytes)
                .set(ResourceServerCapacityDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerCapacityDO::getServerId, serverId));
    }

}
