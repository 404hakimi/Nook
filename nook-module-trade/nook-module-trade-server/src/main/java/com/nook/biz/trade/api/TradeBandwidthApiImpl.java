package com.nook.biz.trade.api;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.resource.ResourceServerQuotaApi;
import com.nook.biz.node.api.resource.dto.ResourceServerQuotaRespDTO;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
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
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;
    @Resource
    private ResourceServerQuotaApi resourceServerQuotaApi;

    @Override
    public int getLandingDesiredBandwidthMbps(String landingServerId) {
        if (StrUtil.isBlank(landingServerId)) {
            return 0;
        }
        // 落地机 1:1 凭证: 直查绑定凭证 → 其订阅 → 套餐带宽 (不再全量扫订阅)
        TradeSubscriptionCertificateDO cert = tradeSubscriptionCertificateService.getByIpId(landingServerId);
        if (ObjectUtil.isNull(cert)) {
            return 0;
        }
        TradeSubscriptionDO sub = subMapper.selectById(cert.getSubscriptionId());
        if (ObjectUtil.isNull(sub) || !TradeSubscriptionStatusEnum.ACTIVE.matches(sub.getStatus())) {
            return 0;
        }
        // 套餐带宽与落地机自身带宽上限取较小: 套餐封顶(不超卖客户没买的), 落地机可往下压; 任一为 0/空 表示该侧不限
        TradePlanDO plan = planMapper.selectById(sub.getPlanId());
        int planBw = ObjectUtil.isNotNull(plan) && ObjectUtil.isNotNull(plan.getBandwidthMbps()) ? plan.getBandwidthMbps() : 0;
        ResourceServerQuotaRespDTO cap = resourceServerQuotaApi.listByServerIds(List.of(landingServerId)).get(landingServerId);
        int landingBw = ObjectUtil.isNotNull(cap) && ObjectUtil.isNotNull(cap.getBandwidthMbps()) ? cap.getBandwidthMbps() : 0;
        if (planBw <= 0) {
            return Math.max(landingBw, 0);
        }
        if (landingBw <= 0) {
            return planBw;
        }
        return Math.min(planBw, landingBw);
    }
}
