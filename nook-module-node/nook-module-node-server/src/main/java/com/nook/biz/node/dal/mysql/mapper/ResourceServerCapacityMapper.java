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

    /**
     * 覆盖写入 NIC 周期累计 (vnstat 报绝对值, 非增量); 同步刷 used_traffic_bytes = rx + tx 兼容老查询.
     */
    default int applyNicTraffic(String serverId, long rxBytes, long txBytes) {
        return update(null, Wrappers.<ResourceServerCapacityDO>lambdaUpdate()
                .set(ResourceServerCapacityDO::getRxBytes, rxBytes)
                .set(ResourceServerCapacityDO::getTxBytes, txBytes)
                .set(ResourceServerCapacityDO::getUsedTrafficBytes, rxBytes + txBytes)
                .set(ResourceServerCapacityDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerCapacityDO::getServerId, serverId));
    }

}
