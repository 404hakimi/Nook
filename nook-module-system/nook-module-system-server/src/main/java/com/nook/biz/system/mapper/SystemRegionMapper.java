package com.nook.biz.system.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.entity.SystemRegionDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 区域字典 Mapper
 *
 * @author nook
 */
@Mapper
public interface SystemRegionMapper extends BaseMapper<SystemRegionDO> {

    default List<SystemRegionDO> selectEnabled() {
        return selectList(Wrappers.<SystemRegionDO>lambdaQuery()
                .eq(SystemRegionDO::getEnabled, 1)
                .orderByAsc(SystemRegionDO::getCode));
    }

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

    default boolean existsByCode(String code) {
        return exists(Wrappers.<SystemRegionDO>lambdaQuery()
                .eq(SystemRegionDO::getCode, code));
    }

    default int renameCode(String oldCode, String newCode) {
        return update(null, Wrappers.<SystemRegionDO>lambdaUpdate()
                .set(SystemRegionDO::getCode, newCode)
                .set(SystemRegionDO::getUpdatedAt, LocalDateTime.now())
                .eq(SystemRegionDO::getCode, oldCode));
    }

    default int updateFields(String code, String countryCode, String countryName,
                             String city, String displayName, String flagEmoji) {
        return update(null, Wrappers.<SystemRegionDO>lambdaUpdate()
                .set(SystemRegionDO::getCountryCode, countryCode)
                .set(SystemRegionDO::getCountryName, countryName)
                .set(SystemRegionDO::getCity, city)
                .set(SystemRegionDO::getDisplayName, displayName)
                .set(SystemRegionDO::getFlagEmoji, flagEmoji)
                .set(SystemRegionDO::getUpdatedAt, LocalDateTime.now())
                .eq(SystemRegionDO::getCode, code));
    }
}
