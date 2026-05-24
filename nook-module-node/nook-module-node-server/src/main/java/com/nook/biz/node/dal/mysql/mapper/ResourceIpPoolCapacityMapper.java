package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCapacityDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * IP 池容量监控 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolCapacityMapper extends BaseMapper<ResourceIpPoolCapacityDO> {

    /**
     * 补丁式更新: 只更新 patch 中非 null 字段, 防全字段覆盖 rx/tx/used_traffic_bytes
     *
     * @param patch 待更新字段 (ipId 必填; 其他 null = 不动)
     * @return 受影响行数
     */
    default int updateBySelective(ResourceIpPoolCapacityDO patch) {
        return update(patch, Wrappers.<ResourceIpPoolCapacityDO>lambdaUpdate()
                .set(ResourceIpPoolCapacityDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolCapacityDO::getIpId, patch.getIpId()));
    }

    /** 批量按 ipId 查; convert 层 enrich 用. */
    default List<ResourceIpPoolCapacityDO> selectByIpIds(Collection<String> ipIds) {
        if (ipIds == null || ipIds.isEmpty()) return List.of();
        return selectBatchIds(ipIds);
    }
}
