package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ResourceIpTypeMapper extends BaseMapper<ResourceIpTypeDO> {

    /** 全量列出, 按 sort_order 升序; 给运营在 IP 池录入 / 套餐配置时下拉用. */
    default List<ResourceIpTypeDO> selectAllOrdered() {
        return selectList(Wrappers.<ResourceIpTypeDO>lambdaQuery()
                .orderByAsc(ResourceIpTypeDO::getSortOrder));
    }

    default ResourceIpTypeDO selectByCode(String code) {
        return selectOne(Wrappers.<ResourceIpTypeDO>lambdaQuery()
                .eq(ResourceIpTypeDO::getCode, code)
                .last("LIMIT 1"));
    }
}
