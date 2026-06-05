package com.nook.biz.trade.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.api.enums.TradeTrafficGrantStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeTrafficGrantDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流量授予 Mapper
 *
 * @author nook
 */
@Mapper
public interface TradeTrafficGrantMapper extends BaseMapper<TradeTrafficGrantDO> {

    /** 某订阅的生效中授予 (按到期升序, 先扣早到期的). */
    default List<TradeTrafficGrantDO> selectActiveBySubscriptionId(String subscriptionId) {
        return selectList(Wrappers.<TradeTrafficGrantDO>lambdaQuery()
                .eq(TradeTrafficGrantDO::getSubscriptionId, subscriptionId)
                .eq(TradeTrafficGrantDO::getStatus, TradeTrafficGrantStatusEnum.ACTIVE.getState())
                .orderByAsc(TradeTrafficGrantDO::getExpiresAt));
    }

    /** 某订阅的全部授予. */
    default List<TradeTrafficGrantDO> selectBySubscriptionId(String subscriptionId) {
        return selectList(Wrappers.<TradeTrafficGrantDO>lambdaQuery()
                .eq(TradeTrafficGrantDO::getSubscriptionId, subscriptionId));
    }
}
