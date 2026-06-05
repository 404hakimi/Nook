package com.nook.biz.trade.service;

import com.nook.biz.trade.dal.dataobject.TradeTrafficGrantDO;

import java.time.LocalDateTime;

/**
 * 流量授予 Service 接口
 *
 * @author nook
 */
public interface TradeTrafficGrantService {

    /**
     * 发放基础额度 (随订阅开通 / 周期重置)
     *
     * @param subscriptionId 订阅ID
     * @param quotaBytes     额度(字节)
     * @param grantedAt      发放时间
     * @param expiresAt      到期时间
     * @return 授予
     */
    TradeTrafficGrantDO createBaseGrant(String subscriptionId, long quotaBytes,
                                        LocalDateTime grantedAt, LocalDateTime expiresAt);

    /**
     * 订阅当前剩余可用额度(字节) = 所有生效授予的 (额度 - 已用) 之和
     *
     * @param subscriptionId 订阅ID
     * @return 剩余字节; ≥0
     */
    long remainingBytes(String subscriptionId);

    /**
     * 累加用量, 按到期升序扣减各笔生效授予 (先扣早到期的); 扣满置已用尽
     *
     * @param subscriptionId 订阅ID
     * @param deltaBytes     本次增量(字节)
     */
    void addUsage(String subscriptionId, long deltaBytes);
}
