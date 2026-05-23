package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 服务器账面 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerBillingMapper extends BaseMapper<ResourceServerBillingDO> {

    default int updateBySelective(ResourceServerBillingDO patch) {
        return update(patch, Wrappers.<ResourceServerBillingDO>lambdaUpdate()
                .set(ResourceServerBillingDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerBillingDO::getServerId, patch.getServerId()));
    }
}
