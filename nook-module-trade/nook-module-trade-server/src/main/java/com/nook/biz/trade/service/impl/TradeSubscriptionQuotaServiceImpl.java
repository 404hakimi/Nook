package com.nook.biz.trade.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.trade.api.enums.TradeQuotaStatusEnum;
import com.nook.biz.trade.api.enums.TradeQuotaTypeEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionQuotaDO;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionQuotaMapper;
import com.nook.biz.trade.service.TradeSubscriptionQuotaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订阅额度账本 Service 实现类
 *
 * @author nook
 */
@Service
public class TradeSubscriptionQuotaServiceImpl implements TradeSubscriptionQuotaService {

    @Resource
    private TradeSubscriptionQuotaMapper tradeSubscriptionQuotaMapper;

    @Override
    public TradeSubscriptionQuotaDO createBaseQuota(String subscriptionId, long totalBytes,
                                                    LocalDateTime startTime, LocalDateTime endTime) {
        TradeSubscriptionQuotaDO quota = new TradeSubscriptionQuotaDO();
        quota.setSubscriptionId(subscriptionId);
        quota.setQuotaType(TradeQuotaTypeEnum.BASE.getType());
        quota.setTotalBytes(totalBytes);
        quota.setUsedBytes(0L);
        quota.setStartTime(startTime);
        quota.setEndTime(endTime);
        quota.setStatus(TradeQuotaStatusEnum.ACTIVE.getState());
        tradeSubscriptionQuotaMapper.insert(quota);
        return quota;
    }

    @Override
    public long remainingBytes(String subscriptionId) {
        LocalDateTime now = LocalDateTime.now();
        long remaining = 0L;
        for (TradeSubscriptionQuotaDO quota : tradeSubscriptionQuotaMapper.selectActiveBySubscriptionId(subscriptionId)) {
            // 跳过已到期的额度; 不计入可用
            if (ObjectUtil.isNull(quota.getEndTime()) || !quota.getEndTime().isAfter(now)) {
                continue;
            }
            long left = quota.getTotalBytes() - quota.getUsedBytes();
            if (left > 0) {
                remaining += left;
            }
        }
        return remaining;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUsage(String subscriptionId, long deltaBytes) {
        if (deltaBytes <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 只扣生效且未到期的额度, 按到期升序(先扣早到期的)
        List<TradeSubscriptionQuotaDO> quotas = new ArrayList<>();
        for (TradeSubscriptionQuotaDO quota : tradeSubscriptionQuotaMapper.selectActiveBySubscriptionId(subscriptionId)) {
            if (ObjectUtil.isNotNull(quota.getEndTime()) && quota.getEndTime().isAfter(now)) {
                quotas.add(quota);
            }
        }
        if (CollUtil.isEmpty(quotas)) {
            return;
        }
        long left = deltaBytes;
        for (TradeSubscriptionQuotaDO quota : quotas) {
            if (left <= 0) {
                break;
            }
            long avail = quota.getTotalBytes() - quota.getUsedBytes();
            if (avail <= 0) {
                continue;
            }
            long take = Math.min(avail, left);
            quota.setUsedBytes(quota.getUsedBytes() + take);
            left -= take;
            if (quota.getUsedBytes() >= quota.getTotalBytes()) {
                quota.setStatus(TradeQuotaStatusEnum.USED_UP.getState());
            }
            tradeSubscriptionQuotaMapper.updateById(quota);
        }
        // 超额: 剩余增量记到最后一笔, 由上层据 remaining<=0 暂停订阅
        if (left > 0) {
            TradeSubscriptionQuotaDO last = quotas.get(quotas.size() - 1);
            last.setUsedBytes(last.getUsedBytes() + left);
            last.setStatus(TradeQuotaStatusEnum.USED_UP.getState());
            tradeSubscriptionQuotaMapper.updateById(last);
        }
    }
}
