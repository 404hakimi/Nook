package com.nook.biz.trade.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.api.enums.TradeQuotaStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionQuotaDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 订阅额度账本 Mapper
 *
 * @author nook
 */
@Mapper
public interface TradeSubscriptionQuotaMapper extends BaseMapper<TradeSubscriptionQuotaDO> {

    /** 某订阅的生效中额度 (按到期升序, 先扣早到期的). */
    default List<TradeSubscriptionQuotaDO> selectActiveBySubscriptionId(String subscriptionId) {
        return selectList(Wrappers.<TradeSubscriptionQuotaDO>lambdaQuery()
                .eq(TradeSubscriptionQuotaDO::getSubscriptionId, subscriptionId)
                .eq(TradeSubscriptionQuotaDO::getStatus, TradeQuotaStatusEnum.ACTIVE.getState())
                .orderByAsc(TradeSubscriptionQuotaDO::getEndTime));
    }

    /** 某订阅的全部额度. */
    default List<TradeSubscriptionQuotaDO> selectBySubscriptionId(String subscriptionId) {
        return selectList(Wrappers.<TradeSubscriptionQuotaDO>lambdaQuery()
                .eq(TradeSubscriptionQuotaDO::getSubscriptionId, subscriptionId));
    }
}
