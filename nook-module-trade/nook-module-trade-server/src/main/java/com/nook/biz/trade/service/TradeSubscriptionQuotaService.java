package com.nook.biz.trade.service;

import com.nook.biz.trade.dal.dataobject.TradeSubscriptionQuotaDO;

import java.time.LocalDateTime;

/**
 * 订阅额度账本 Service 接口
 *
 * @author nook
 */
public interface TradeSubscriptionQuotaService {

    /**
     * 发放基础额度 (随订阅开通 / 周期重置)
     *
     * @param subscriptionId 订阅ID
     * @param totalBytes     额度(字节)
     * @param startTime      生效时间
     * @param endTime        到期时间
     * @return 额度
     */
    TradeSubscriptionQuotaDO createBaseQuota(String subscriptionId, long totalBytes,
                                             LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 订阅当前剩余可用额度(字节) = 所有生效额度的 (额度 - 已用) 之和
     *
     * @param subscriptionId 订阅ID
     * @return 剩余字节; ≥0
     */
    long remainingBytes(String subscriptionId);

    /**
     * 累加用量, 按到期升序扣减各笔生效额度 (先扣早到期的); 扣满置已用尽
     *
     * @param subscriptionId 订阅ID
     * @param deltaBytes     本次增量(字节)
     */
    void addUsage(String subscriptionId, long deltaBytes);
}
