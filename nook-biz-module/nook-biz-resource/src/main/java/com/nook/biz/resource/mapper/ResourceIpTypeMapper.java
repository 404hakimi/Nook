package com.nook.biz.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.resource.entity.ResourceIpType;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ResourceIpTypeMapper extends BaseMapper<ResourceIpType> {

    /** 全量列出, 按 sort_order 升序; 给运营在 IP 池录入 / 套餐配置时下拉用. */
    default List<ResourceIpType> selectAllOrdered() {
        return selectList(Wrappers.<ResourceIpType>lambdaQuery()
                .orderByAsc(ResourceIpType::getSortOrder));
    }

    default ResourceIpType selectByCode(String code) {
        return selectOne(Wrappers.<ResourceIpType>lambdaQuery()
                .eq(ResourceIpType::getCode, code)
                .last("LIMIT 1"));
    }
}
