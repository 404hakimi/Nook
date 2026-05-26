package com.nook.biz.system.dal.mysql.mapper.region;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.dal.dataobject.region.SystemRegionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 区域字典 Mapper
 *
 * @author nook
 */
@Mapper
public interface SystemRegionMapper extends BaseMapper<SystemRegionDO> {

    /** 已启用区域列表 (admin 下拉用); 按 code 升序. */
    default List<SystemRegionDO> selectEnabled() {
        return selectList(Wrappers.<SystemRegionDO>lambdaQuery()
                .eq(SystemRegionDO::getEnabled, 1)
                .orderByAsc(SystemRegionDO::getCode));
    }

    /** 全量列表 (admin 管理用); keyword 模糊匹 code/countryName/city/displayName; enabled 精确过滤. */
    default List<SystemRegionDO> selectByQuery(String keyword, Integer enabled) {
        return selectList(Wrappers.<SystemRegionDO>lambdaQuery()
                .eq(ObjectUtil.isNotNull(enabled), SystemRegionDO::getEnabled, enabled)
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(SystemRegionDO::getCode, keyword)
                        .or().like(SystemRegionDO::getCountryName, keyword)
                        .or().like(SystemRegionDO::getCity, keyword)
                        .or().like(SystemRegionDO::getDisplayName, keyword))
                .orderByAsc(SystemRegionDO::getCode));
    }
}
