package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolBillingDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * IP 池账面 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolBillingMapper extends BaseMapper<ResourceIpPoolBillingDO> {

    default int updateBySelective(ResourceIpPoolBillingDO patch) {
        return update(patch, Wrappers.<ResourceIpPoolBillingDO>lambdaUpdate()
                .set(ResourceIpPoolBillingDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolBillingDO::getIpId, patch.getIpId()));
    }
}
