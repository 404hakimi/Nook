package com.nook.biz.trade.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.trade.api.enums.TradeCertStatusEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.config.TradeJobProperties;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.event.SubscriptionMachineChangeEvent;
import com.nook.biz.trade.service.TradeAllocator;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 落地机故障切换定时任务: 到顶 / 掉线的落地机, 把独占用户整迁到有量的新落地机 (设计见 docs landing-budget 闸三)
 *
 * @author nook
 */
@Slf4j
@Component
public class LandingFailoverJob {

    @Resource
    private TradeJobProperties tradeJobProperties;
    @Resource
    private ResourceServerApi serverApi;
    @Resource
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;
    @Resource
    private TradeSubscriptionMapper subMapper;
    @Resource
    private TradePlanMapper planMapper;
    @Resource
    private TradeAllocator allocator;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "#{@tradeJobProperties.landingFailover.cron}")
    public void check() {
        if (!tradeJobProperties.getLandingFailover().isEnabled()) {
            return;
        }
        Map<String, String> faulted = serverApi.findLandingsNeedingFailover();
        if (CollUtil.isEmpty(faulted)) {
            return;
        }
        for (Map.Entry<String, String> e : faulted.entrySet()) {
            try {
                this.evacuate(e.getKey(), e.getValue());
            } catch (Exception ex) {
                log.error("[landingFailover] 疏散落地机异常 landing={} reason={}: {}", e.getKey(), e.getValue(), ex.getMessage(), ex);
            }
        }
    }

    /** 把故障落地机上的独占用户整迁到一台有量的新落地机; 无目标则留原机 + 告警. */
    private void evacuate(String faultedIpId, String reason) {
        // 落地机独占: 一台至多一个活跃凭证; 为空说明上一轮已迁走, 直接结束
        TradeSubscriptionCertificateDO cert = tradeSubscriptionCertificateService.getByIpId(faultedIpId);
        if (ObjectUtil.isNull(cert) || !TradeCertStatusEnum.ACTIVE.matches(cert.getCertStatus())) {
            return;
        }
        // 只迁活跃订阅
        TradeSubscriptionDO sub = subMapper.selectById(cert.getSubscriptionId());
        if (ObjectUtil.isNull(sub) || !TradeSubscriptionStatusEnum.ACTIVE.matches(sub.getStatus())) {
            return;
        }
        TradePlanDO plan = planMapper.selectById(sub.getPlanId());
        if (ObjectUtil.isNull(plan)) {
            return;
        }
        int planTraffic = ObjectUtil.isNull(plan.getTrafficGb()) ? 0 : plan.getTrafficGb();
        int planBw = ObjectUtil.isNull(plan.getBandwidthMbps()) ? 0 : plan.getBandwidthMbps();
        // 掉线=机器故障; 到顶=流量耗尽
        TradeSubscriptionChangeReasonEnum chgReason = ResourceServerThrottleStateEnum.THROTTLED.matches(reason)
                ? TradeSubscriptionChangeReasonEnum.TRAFFIC_EXHAUSTED
                : TradeSubscriptionChangeReasonEnum.FAULT;
        // 候选新落地机(同区域 + IP类型 + 本月预算/带宽达标 + 健康); 故障源已被准入网关排除, 不会被选回
        List<String> candidates = allocator.matchLandings(plan.getRegionCode(), plan.getIpTypeId(), planTraffic, planBw);
        // 逐台试占: 只换落地机, 线路机不变(复用凭证现有 server_id); 撞 uk_cert_ip 说明被并发占, 试下一台
        for (String targetIpId : candidates) {
            try {
                tradeSubscriptionCertificateService.setAllocation(cert.getId(), cert.getServerId(), targetIpId);
            } catch (DuplicateKeyException ex) {
                log.warn("[landingFailover] 落地机 {} 被并发抢占, 试下一台", targetIpId);
                continue;
            }
            eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(
                    sub.getId(), sub.getMemberUserId(), TradeSubscriptionChangeTypeEnum.LANDING,
                    faultedIpId, targetIpId, chgReason, "system-failover"));
            log.info("[landingFailover] 迁移用户 sub={} cert={} 落地机 {} → {} reason={}",
                    sub.getId(), cert.getId(), faultedIpId, targetIpId, reason);
            return;
        }
        // 无目标兜底: 留原机 + 告警 (不硬塞/不跨区, 同 frontline 决策)
        log.error("[landingFailover] 无同区有量落地机可迁, 用户留原机告警: sub={} member={} region={} faulted={} reason={}",
                sub.getId(), sub.getMemberUserId(), plan.getRegionCode(), faultedIpId, reason);
    }
}
