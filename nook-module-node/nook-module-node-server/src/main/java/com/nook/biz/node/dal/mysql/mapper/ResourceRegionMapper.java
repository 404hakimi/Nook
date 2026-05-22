package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceRegionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 资源区域 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceRegionMapper extends BaseMapper<ResourceRegionDO> {

    /** 已启用区域列表 (admin 下拉用); 按 code 升序. */
    default List<ResourceRegionDO> selectEnabled() {
        return selectList(Wrappers.<ResourceRegionDO>lambdaQuery()
                .eq(ResourceRegionDO::getEnabled, 1)
                .orderByAsc(ResourceRegionDO::getCode));
    }

    /** 全量列表 (admin 管理用); keyword 模糊匹 code/countryName/city/displayName; enabled 精确过滤. */
    default List<ResourceRegionDO> selectByQuery(String keyword, Integer enabled) {
        return selectList(Wrappers.<ResourceRegionDO>lambdaQuery()
                .eq(ObjectUtil.isNotNull(enabled), ResourceRegionDO::getEnabled, enabled)
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(ResourceRegionDO::getCode, keyword)
                        .or().like(ResourceRegionDO::getCountryName, keyword)
                        .or().like(ResourceRegionDO::getCity, keyword)
                        .or().like(ResourceRegionDO::getDisplayName, keyword))
                .orderByAsc(ResourceRegionDO::getCode));
    }
}
