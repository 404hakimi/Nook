package com.nook.biz.trade.api;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.xray.XrayClientNodeApi;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link TradeBandwidthApi} 实现; 落地机 1:1, 限速值取占用它的 ACTIVE 订阅的套餐带宽.
 *
 * @author nook
 */
@Service
public class TradeBandwidthApiImpl implements TradeBandwidthApi {

    @Resource
    private TradeSubscriptionMapper subMapper;
    @Resource
    private TradePlanMapper planMapper;
    @Resource
    private XrayClientNodeApi clientNodeApi;

    @Override
    public int getLandingDesiredBandwidthMbps(String landingServerId) {
        if (StrUtil.isBlank(landingServerId)) {
            return 0;
        }
        List<TradeSubscriptionDO> active = subMapper.selectAllActive();
        if (active.isEmpty()) {
            return 0;
        }
        Set<String> clientIds = CollectionUtils.convertSet(
                active, TradeSubscriptionDO::getXrayClientId, s -> s.getXrayClientId() != null);
        Map<String, String> landingByClient = clientNodeApi.getLandingIdByClientIds(clientIds);
        for (TradeSubscriptionDO s : active) {
            if (landingServerId.equals(landingByClient.get(s.getXrayClientId()))) {
                TradePlanDO plan = planMapper.selectById(s.getPlanId());
                return plan != null && plan.getBandwidthMbps() != null ? plan.getBandwidthMbps() : 0;
            }
        }
        return 0;
    }
}
