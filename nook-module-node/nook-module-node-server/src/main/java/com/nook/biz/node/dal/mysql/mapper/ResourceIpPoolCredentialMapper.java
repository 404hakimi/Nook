package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCredentialDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * IP 池 SSH 凭据 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolCredentialMapper extends BaseMapper<ResourceIpPoolCredentialDO> {

    default int updateBySelective(ResourceIpPoolCredentialDO patch) {
        return update(patch, Wrappers.<ResourceIpPoolCredentialDO>lambdaUpdate()
                .set(ResourceIpPoolCredentialDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolCredentialDO::getIpId, patch.getIpId()));
    }
}
