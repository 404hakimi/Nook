package com.nook.biz.trade.api;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.xray.XrayClientNodeApi;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 落地机限速值解析 Service 实现类
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
    @Resource
    private ResourceServerCapacityApi capacityApi;

    @Override
    public int getLandingDesiredBandwidthMbps(String landingServerId) {
        if (StrUtil.isBlank(landingServerId)) {
            return 0;
        }
        // 落地机 1:1: 直查绑定客户端 → 其生效订阅 → 套餐带宽 (不再全量扫订阅)
        String clientId = clientNodeApi.getClientIdByLandingId(landingServerId);
        if (clientId == null) {
            return 0;
        }
        TradeSubscriptionDO sub = subMapper.selectActiveByClientId(clientId);
        if (sub == null) {
            return 0;
        }
        // 套餐带宽与落地机自身带宽上限取较小: 套餐封顶(不超卖客户没买的), 落地机可往下压; 任一为 0/null 表示该侧不限
        TradePlanDO plan = planMapper.selectById(sub.getPlanId());
        int planBw = plan != null && plan.getBandwidthMbps() != null ? plan.getBandwidthMbps() : 0;
        ResourceServerCapacityRespDTO cap = capacityApi.listByServerIds(List.of(landingServerId)).get(landingServerId);
        int landingBw = cap != null && cap.getBandwidthLimitMbps() != null ? cap.getBandwidthLimitMbps() : 0;
        if (planBw <= 0) {
            return Math.max(landingBw, 0);
        }
        if (landingBw <= 0) {
            return planBw;
        }
        return Math.min(planBw, landingBw);
    }
}
