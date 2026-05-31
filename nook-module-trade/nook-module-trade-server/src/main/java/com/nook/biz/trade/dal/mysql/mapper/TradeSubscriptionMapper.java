package com.nook.biz.trade.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import cn.hutool.core.collection.CollUtil;
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
        return selectPage(page, Wrappers.<TradeSubscriptionDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(memberUserId), TradeSubscriptionDO::getMemberUserId, memberUserId)
                .eq(StrUtil.isNotBlank(planId), TradeSubscriptionDO::getPlanId, planId)
                .eq(StrUtil.isNotBlank(status), TradeSubscriptionDO::getStatus, status)
                .orderByDesc(TradeSubscriptionDO::getCreatedAt));
    }
}
