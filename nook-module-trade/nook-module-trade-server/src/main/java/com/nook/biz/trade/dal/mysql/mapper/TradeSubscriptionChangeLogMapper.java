package com.nook.biz.trade.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionChangeLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订阅换机历史日志 Mapper.
 *
 * @author nook
 */
@Mapper
public interface TradeSubscriptionChangeLogMapper extends BaseMapper<TradeSubscriptionChangeLogDO> {
}
