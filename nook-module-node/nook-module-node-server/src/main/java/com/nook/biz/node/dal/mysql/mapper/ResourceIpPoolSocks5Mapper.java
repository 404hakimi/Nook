package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolSocks5DO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IP 池 dante 配置 + 限速 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolSocks5Mapper extends BaseMapper<ResourceIpPoolSocks5DO> {

    default int updateBySelective(ResourceIpPoolSocks5DO patch) {
        return update(patch, Wrappers.<ResourceIpPoolSocks5DO>lambdaUpdate()
                .set(ResourceIpPoolSocks5DO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolSocks5DO::getIpId, patch.getIpId()));
    }

    /** 批量查 (admin 列表展示 dante 端口/限速 用). */
    default List<ResourceIpPoolSocks5DO> selectByIpIds(java.util.Collection<String> ipIds) {
        return selectBatchIds(ipIds);
    }
}
