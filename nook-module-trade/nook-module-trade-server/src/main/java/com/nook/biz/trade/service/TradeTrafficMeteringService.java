package com.nook.biz.trade.service;

import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.trade.dal.dataobject.MemberPlanTrafficDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订阅流量计量 Service 接口
 *
 * @author nook
 */
public interface TradeTrafficMeteringService {

    /**
     * 批量预载一轮计量所需只读数据 (套餐流量上限 / 落地机映射 / 落地机容量 / 订阅用量行).
     *
     * @param subs 本轮待处理订阅
     * @return 计量上下文
     */
    MeteringContext preload(List<TradeSubscriptionDO> subs);

    /**
     * 把落地机业务流量增量累加进订阅用量 (到周期点则清零重打基线). 返回是否已达套餐流量上限.
     *
     * @param sub 订阅
     * @param now 当前时刻
     * @param ctx 计量上下文
     * @return true = 已达上限 (调用方据此停服)
     */
    boolean accumulate(TradeSubscriptionDO sub, LocalDateTime now, MeteringContext ctx);

    /**
     * 停服订阅到重置点则清零用量 + 重打基线 + 推下一周期. 返回是否本轮已重置.
     *
     * @param sub 订阅
     * @param now 当前时刻
     * @param ctx 计量上下文
     * @return true = 已重置 (调用方据此复活)
     */
    boolean tryCycleReset(TradeSubscriptionDO sub, LocalDateTime now, MeteringContext ctx);

    /**
     * 一轮计量的只读上下文 (循环前批量载入, 避免逐订阅查).
     */
    record MeteringContext(
            Map<String, Integer> planTrafficGb,
            Map<String, String> landingBySub,
            Map<String, ResourceServerCapacityRespDTO> capMap,
            Map<String, MemberPlanTrafficDO> trafficBySub) {
    }
}
