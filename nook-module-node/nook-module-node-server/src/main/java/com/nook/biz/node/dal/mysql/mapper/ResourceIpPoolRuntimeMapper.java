package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolRuntimeDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * IP 池 agent 心跳 + 健康 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolRuntimeMapper extends BaseMapper<ResourceIpPoolRuntimeDO> {

    default int updateBySelective(ResourceIpPoolRuntimeDO patch) {
        return update(patch, Wrappers.<ResourceIpPoolRuntimeDO>lambdaUpdate()
                .set(ResourceIpPoolRuntimeDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolRuntimeDO::getIpId, patch.getIpId()));
    }
}
