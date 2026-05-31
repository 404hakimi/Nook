package com.nook.biz.trade.dal.mysql.mapper;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 套餐 Mapper
 *
 * @author nook
 */
@Mapper
public interface TradePlanMapper extends BaseMapper<TradePlanDO> {

    default boolean existsByCode(String code) {
        return exists(Wrappers.<TradePlanDO>lambdaQuery()
                .eq(TradePlanDO::getCode, code));
    }

    default boolean existsByCodeExcludingId(String code, String excludeId) {
        return exists(Wrappers.<TradePlanDO>lambdaQuery()
                .eq(TradePlanDO::getCode, code)
                .ne(TradePlanDO::getId, excludeId));
    }

    /** 分页 (区域 / IP 类型筛选套餐自身字段). */
    default IPage<TradePlanDO> selectPageByQuery(IPage<TradePlanDO> page, List<String> regionCodes,
                                                 String ipTypeId, Integer enabled, String keyword) {
        return selectPage(page, Wrappers.<TradePlanDO>lambdaQuery()
                .in(CollUtil.isNotEmpty(regionCodes), TradePlanDO::getRegionCode, regionCodes)
                .eq(StrUtil.isNotBlank(ipTypeId), TradePlanDO::getIpTypeId, ipTypeId)
                .eq(enabled != null, TradePlanDO::getEnabled, enabled)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(TradePlanDO::getName, keyword)
                        .or().like(TradePlanDO::getCode, keyword))
                .orderByDesc(TradePlanDO::getCreatedAt));
    }

    /** 按区域码统计套餐数; 返回 区域码 → 套餐数. */
    default Map<String, Long> countGroupByRegion() {
        List<Map<String, Object>> rows = selectMaps(Wrappers.<TradePlanDO>query()
                .select("region_code", "COUNT(*) AS cnt")
                .isNotNull("region_code")
                .ne("region_code", "")
                .groupBy("region_code"));
        Map<String, Long> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("region_code"), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    /** 区域码迁移: 旧码套餐改挂新码 (区域更正级联用); Wrapper 更新须显式 set updated_at. */
    default int migrateRegionCode(String oldCode, String newCode) {
        return update(null, Wrappers.<TradePlanDO>lambdaUpdate()
                .set(TradePlanDO::getRegionCode, newCode)
                .set(TradePlanDO::getUpdatedAt, LocalDateTime.now())
                .eq(TradePlanDO::getRegionCode, oldCode));
    }
}
