package com.nook.biz.trade.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅 Mapper
 *
 * @author nook
 */
@Mapper
public interface TradeSubscriptionMapper extends BaseMapper<TradeSubscriptionDO> {

    /** 某会员所有生效中订阅. */
    default List<TradeSubscriptionDO> selectActiveByMember(String memberUserId) {
        return selectList(Wrappers.<TradeSubscriptionDO>lambdaQuery()
                .eq(TradeSubscriptionDO::getMemberUserId, memberUserId)
                .eq(TradeSubscriptionDO::getStatus, TradeSubscriptionStatusEnum.ACTIVE.getState()));
    }

    /** 所有生效中订阅. */
    default List<TradeSubscriptionDO> selectAllActive() {
        return selectList(Wrappers.<TradeSubscriptionDO>lambdaQuery()
                .eq(TradeSubscriptionDO::getStatus, TradeSubscriptionStatusEnum.ACTIVE.getState()));
    }

    /** 生效中 + 停服 订阅; 计量 job 扫描用 (停服订阅到重置点要恢复). */
    default List<TradeSubscriptionDO> selectActiveOrSuspended() {
        return selectList(Wrappers.<TradeSubscriptionDO>lambdaQuery()
                .in(TradeSubscriptionDO::getStatus,
                        TradeSubscriptionStatusEnum.ACTIVE.getState(),
                        TradeSubscriptionStatusEnum.SUSPENDED.getState()));
    }

    /** 各套餐生效订阅数 (GROUP BY 聚合, 不全量加载). */
    default Map<String, Integer> countActiveGroupByPlan() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : selectMaps(Wrappers.<TradeSubscriptionDO>query()
                .select("plan_id AS planId", "COUNT(*) AS cnt")
                .eq("status", TradeSubscriptionStatusEnum.ACTIVE.getState())
                .groupBy("plan_id"))) {
            Object planId = row.get("planId");
            if (ObjectUtil.isNotNull(planId)) {
                counts.put(planId.toString(), ((Number) row.get("cnt")).intValue());
            }
        }
        return counts;
    }

    /** 生效中且已过期的订阅. */
    default List<TradeSubscriptionDO> selectExpiredCandidates(LocalDateTime now) {
        return selectList(Wrappers.<TradeSubscriptionDO>lambdaQuery()
                .eq(TradeSubscriptionDO::getStatus, TradeSubscriptionStatusEnum.ACTIVE.getState())
                .lt(TradeSubscriptionDO::getExpiresAt, now));
    }

    /** 批量统计指定套餐的生效中订阅数 (仅本页 planIds, 不全量扫). */
    default Map<String, Integer> countActiveByPlanIds(Collection<String> planIds) {
        if (CollUtil.isEmpty(planIds)) {
            return new HashMap<>();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (TradeSubscriptionDO sub : selectList(Wrappers.<TradeSubscriptionDO>lambdaQuery()
                .select(TradeSubscriptionDO::getPlanId)
                .in(TradeSubscriptionDO::getPlanId, planIds)
                .eq(TradeSubscriptionDO::getStatus, TradeSubscriptionStatusEnum.ACTIVE.getState()))) {
            counts.merge(sub.getPlanId(), 1, Integer::sum);
        }
        return counts;
    }

    /** 某套餐是否还有生效中订阅. */
    default boolean existsActiveByPlan(String planId) {
        return exists(Wrappers.<TradeSubscriptionDO>lambdaQuery()
                .eq(TradeSubscriptionDO::getPlanId, planId)
                .eq(TradeSubscriptionDO::getStatus, TradeSubscriptionStatusEnum.ACTIVE.getState()));
    }

    default IPage<TradeSubscriptionDO> selectPageByQuery(IPage<TradeSubscriptionDO> page,
                                                         String memberUserId, String planId, String status) {
        // 生效中靠前, 同状态内按开通时间倒序
        return selectPage(page, Wrappers.<TradeSubscriptionDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(memberUserId), TradeSubscriptionDO::getMemberUserId, memberUserId)
                .eq(StrUtil.isNotBlank(planId), TradeSubscriptionDO::getPlanId, planId)
                .eq(StrUtil.isNotBlank(status), TradeSubscriptionDO::getStatus, status)
                .last("ORDER BY status = '" + TradeSubscriptionStatusEnum.ACTIVE.getState()
                        + "' DESC, started_at DESC"));
    }
}
