package com.nook.biz.trade.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import org.apache.ibatis.annotations.Mapper;

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
    default IPage<TradePlanDO> selectPageByQuery(IPage<TradePlanDO> page, String regionCode,
                                                 String ipTypeId, Integer enabled, String keyword) {
        return selectPage(page, Wrappers.<TradePlanDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(regionCode), TradePlanDO::getRegionCode, regionCode)
                .eq(StrUtil.isNotBlank(ipTypeId), TradePlanDO::getIpTypeId, ipTypeId)
                .eq(enabled != null, TradePlanDO::getEnabled, enabled)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(TradePlanDO::getName, keyword)
                        .or().like(TradePlanDO::getCode, keyword))
                .orderByDesc(TradePlanDO::getCreatedAt));
    }
}
