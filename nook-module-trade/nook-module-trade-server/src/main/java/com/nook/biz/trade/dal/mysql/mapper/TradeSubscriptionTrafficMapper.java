package com.nook.biz.trade.dal.mysql.mapper;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionTrafficDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 订阅接入点流量计量 Mapper
 *
 * @author nook
 */
@Mapper
public interface TradeSubscriptionTrafficMapper extends BaseMapper<TradeSubscriptionTrafficDO> {

    /** 某接入点的当周期行(end_time 为空); 无返 null. */
    default TradeSubscriptionTrafficDO selectCurrentByCertId(String certId) {
        return selectOne(Wrappers.<TradeSubscriptionTrafficDO>lambdaQuery()
                .eq(TradeSubscriptionTrafficDO::getCertId, certId)
                .isNull(TradeSubscriptionTrafficDO::getEndTime));
    }

    /** 一批订阅各接入点的当周期行(end_time 为空); 用于按订阅汇总展示. */
    default List<TradeSubscriptionTrafficDO> selectCurrentBySubscriptionIds(Collection<String> subscriptionIds) {
        if (CollUtil.isEmpty(subscriptionIds)) {
            return List.of();
        }
        return selectList(Wrappers.<TradeSubscriptionTrafficDO>lambdaQuery()
                .in(TradeSubscriptionTrafficDO::getSubscriptionId, subscriptionIds)
                .isNull(TradeSubscriptionTrafficDO::getEndTime));
    }
}
