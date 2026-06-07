package com.nook.biz.trade.service;

import com.nook.biz.node.api.resource.dto.ResourceServerQuotaRespDTO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionTrafficDO;

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
     * 批量预载一轮计量所需只读数据 (套餐流量上限 / 订阅接入点 / 落地机测量 / 接入点当周期行).
     *
     * @param subs 本轮待处理订阅
     * @return 计量上下文
     */
    MeteringContext preload(List<TradeSubscriptionDO> subs);

    /**
     * 按接入点累加用户上下行增量进订阅额度 (跨周期则翻篇发新基础额度). 返回订阅是否已耗尽额度.
     *
     * @param sub 订阅
     * @param now 当前时刻
     * @param ctx 计量上下文
     * @return true = 已耗尽 (调用方据此停服)
     */
    boolean accumulate(TradeSubscriptionDO sub, LocalDateTime now, MeteringContext ctx);

    /**
     * 停服订阅到下周期则翻篇 (接入点行封存开新行 + 发新基础额度). 返回是否本轮已翻篇.
     *
     * @param sub 订阅
     * @param now 当前时刻
     * @param ctx 计量上下文
     * @return true = 已翻篇 (调用方据此复活)
     */
    boolean tryCycleReset(TradeSubscriptionDO sub, LocalDateTime now, MeteringContext ctx);

    /**
     * 一轮计量的只读上下文 (循环前批量载入, 避免逐订阅查).
     */
    record MeteringContext(
            Map<String, Integer> planTrafficGb,
            Map<String, List<TradeSubscriptionCertificateDO>> certsBySub,
            Map<String, ResourceServerQuotaRespDTO> capByLanding,
            Map<String, TradeSubscriptionTrafficDO> trafficByCert) {
    }
}
