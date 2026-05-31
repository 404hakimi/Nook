package com.nook.biz.system.dal.mysql.mapper.region;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.dal.dataobject.region.SystemRegionDO;
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

    /** 区域码(主键)改名; updateById 改不了主键, 用 Wrapper set; Wrapper 更新须显式 set updated_at. */
    default int renameCode(String oldCode, String newCode) {
        return update(null, Wrappers.<SystemRegionDO>lambdaUpdate()
                .set(SystemRegionDO::getCode, newCode)
                .set(SystemRegionDO::getUpdatedAt, LocalDateTime.now())
                .eq(SystemRegionDO::getCode, oldCode));
    }

    /** 更新区域展示字段 (city 可清空 → 显式 set 绕 NOT_NULL 策略); Wrapper 更新须显式 set updated_at. */
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
