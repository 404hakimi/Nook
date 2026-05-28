package com.nook.biz.trade.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.dal.dataobject.TradePlanResourceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 套餐资源池关联 Mapper
 *
 * @author nook
 */
@Mapper
public interface TradePlanResourceMapper extends BaseMapper<TradePlanResourceDO> {

    /** 取某套餐关联的资源 (resourceType 可空 = 不限类型). */
    default List<TradePlanResourceDO> selectByPlan(String planId, String resourceType) {
        return selectList(Wrappers.<TradePlanResourceDO>lambdaQuery()
                .eq(TradePlanResourceDO::getTradePlanId, planId)
                .eq(StrUtil.isNotBlank(resourceType), TradePlanResourceDO::getResourceType, resourceType));
    }

    default boolean existsByPlanAndResource(String planId, String resourceType, String resourceId) {
        return exists(Wrappers.<TradePlanResourceDO>lambdaQuery()
                .eq(TradePlanResourceDO::getTradePlanId, planId)
                .eq(TradePlanResourceDO::getResourceType, resourceType)
                .eq(TradePlanResourceDO::getResourceId, resourceId));
    }
}
