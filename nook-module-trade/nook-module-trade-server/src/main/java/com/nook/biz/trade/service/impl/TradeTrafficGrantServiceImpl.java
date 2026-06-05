package com.nook.biz.trade.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.trade.api.enums.TradeTrafficGrantStatusEnum;
import com.nook.biz.trade.api.enums.TradeTrafficGrantTypeEnum;
import com.nook.biz.trade.dal.dataobject.TradeTrafficGrantDO;
import com.nook.biz.trade.dal.mysql.mapper.TradeTrafficGrantMapper;
import com.nook.biz.trade.service.TradeTrafficGrantService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 流量授予 Service 实现类
 *
 * @author nook
 */
@Service
public class TradeTrafficGrantServiceImpl implements TradeTrafficGrantService {

    @Resource
    private TradeTrafficGrantMapper tradeTrafficGrantMapper;

    @Override
    public TradeTrafficGrantDO createBaseGrant(String subscriptionId, long quotaBytes,
                                               LocalDateTime grantedAt, LocalDateTime expiresAt) {
        TradeTrafficGrantDO grant = new TradeTrafficGrantDO();
        grant.setSubscriptionId(subscriptionId);
        grant.setGrantType(TradeTrafficGrantTypeEnum.BASE.getType());
        grant.setQuotaBytes(quotaBytes);
        grant.setUsedBytes(0L);
        grant.setGrantedAt(grantedAt);
        grant.setExpiresAt(expiresAt);
        grant.setStatus(TradeTrafficGrantStatusEnum.ACTIVE.getState());
        tradeTrafficGrantMapper.insert(grant);
        return grant;
    }

    @Override
    public long remainingBytes(String subscriptionId) {
        LocalDateTime now = LocalDateTime.now();
        long remaining = 0L;
        for (TradeTrafficGrantDO grant : tradeTrafficGrantMapper.selectActiveBySubscriptionId(subscriptionId)) {
            // 跳过到时间的授予; 不计入可用额度
            if (ObjectUtil.isNull(grant.getExpiresAt()) || !grant.getExpiresAt().isAfter(now)) {
                continue;
            }
            long left = grant.getQuotaBytes() - grant.getUsedBytes();
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
        // 只扣生效且未到时间的授予, 按到期升序(先扣早到期的)
        List<TradeTrafficGrantDO> grants = new ArrayList<>();
        for (TradeTrafficGrantDO grant : tradeTrafficGrantMapper.selectActiveBySubscriptionId(subscriptionId)) {
            if (ObjectUtil.isNotNull(grant.getExpiresAt()) && grant.getExpiresAt().isAfter(now)) {
                grants.add(grant);
            }
        }
        if (CollUtil.isEmpty(grants)) {
            return;
        }
        long left = deltaBytes;
        for (TradeTrafficGrantDO grant : grants) {
            if (left <= 0) {
                break;
            }
            long avail = grant.getQuotaBytes() - grant.getUsedBytes();
            if (avail <= 0) {
                continue;
            }
            long take = Math.min(avail, left);
            grant.setUsedBytes(grant.getUsedBytes() + take);
            left -= take;
            if (grant.getUsedBytes() >= grant.getQuotaBytes()) {
                grant.setStatus(TradeTrafficGrantStatusEnum.USED_UP.getState());
            }
            tradeTrafficGrantMapper.updateById(grant);
        }
        // 超额: 剩余增量记到最后一笔, 由上层据 remaining<=0 暂停订阅
        if (left > 0) {
            TradeTrafficGrantDO last = grants.get(grants.size() - 1);
            last.setUsedBytes(last.getUsedBytes() + left);
            last.setStatus(TradeTrafficGrantStatusEnum.USED_UP.getState());
            tradeTrafficGrantMapper.updateById(last);
        }
    }
}
