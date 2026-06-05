package com.nook.biz.trade.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.event.SubscriptionMachineChangeEvent;
import com.nook.biz.trade.config.TradeJobProperties;
import com.nook.biz.trade.service.TradeAllocator;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 线路机故障切换定时任务: 到顶 / 掉线的线路机, 把存量接入点迁到同区域健康线路机 (设计见 docs 11)
 *
 * @author nook
 */
@Slf4j
@Component
public class FrontlineFailoverJob {

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

    @Scheduled(cron = "#{@tradeJobProperties.failover.cron}")
    public void check() {
        if (!tradeJobProperties.getFailover().isEnabled()) {
            return;
        }
        Map<String, String> faulted = serverApi.findFrontlinesNeedingFailover();
        if (CollUtil.isEmpty(faulted)) {
            return;
        }
        for (Map.Entry<String, String> e : faulted.entrySet()) {
            try {
                this.evacuate(e.getKey(), e.getValue());
            } catch (Exception ex) {
                log.error("[failover] 疏散线路机异常 server={} reason={}: {}", e.getKey(), e.getValue(), ex.getMessage(), ex);
            }
        }
    }

    /** 把故障线路机上的接入点逐个迁到同区域健康线路机; 无目标则留原机 + 告警. */
    private void evacuate(String faultedServer, String reason) {
        // 该线路机上应运行的凭证(每个=一个接入点); 故障源后续被准入网关自动排除
        List<TradeSubscriptionCertificateDO> certs =
                tradeSubscriptionCertificateService.listActiveByServer(faultedServer);
        if (CollUtil.isEmpty(certs)) {
            return;
        }
        // 凭证 → 所属订阅 + 套餐, 批量查一次
        Map<String, TradeSubscriptionDO> subMap = CollectionUtils.convertMap(
                subMapper.selectBatchIds(CollectionUtils.convertSet(certs, TradeSubscriptionCertificateDO::getSubscriptionId)),
                TradeSubscriptionDO::getId);
        Map<String, TradePlanDO> planMap = CollectionUtils.convertMap(
                planMapper.selectBatchIds(CollectionUtils.convertSet(subMap.values(), TradeSubscriptionDO::getPlanId)),
                TradePlanDO::getId);
        // 掉线=尽快迁完; 到顶=每轮限量迁移(渐进, 用剩余余量作缓冲)
        boolean urgent = !ResourceServerThrottleStateEnum.THROTTLED.matches(reason);
        int budget = urgent ? certs.size()
                : Math.min(tradeJobProperties.getFailover().getThrottledBatch(), certs.size());
        TradeSubscriptionChangeReasonEnum chgReason = urgent
                ? TradeSubscriptionChangeReasonEnum.FAULT
                : TradeSubscriptionChangeReasonEnum.TRAFFIC_EXHAUSTED;

        int moved = 0;
        for (TradeSubscriptionCertificateDO cert : certs) {
            if (moved >= budget) {
                break;
            }
            TradeSubscriptionDO sub = subMap.get(cert.getSubscriptionId());
            // 只迁活跃订阅的接入点
            if (ObjectUtil.isNull(sub) || !TradeSubscriptionStatusEnum.ACTIVE.matches(sub.getStatus())) {
                continue;
            }
            TradePlanDO plan = planMap.get(sub.getPlanId());
            if (ObjectUtil.isNull(plan)) {
                continue;
            }
            int bw = ObjectUtil.isNull(plan.getBandwidthMbps()) ? 0 : plan.getBandwidthMbps();
            // 复用分配策略选目标; 故障源已被统一准入网关排除(到顶/掉线), 不会被选回; 逐个迁→各线路机已挂载量实时变→自然分散
            String target = allocator.pickFrontline(plan.getRegionCode(), bw);
            if (ObjectUtil.isNull(target)) {
                // 无目标兜底: 留原机 + 告警 (不硬塞/不跨区, 决策见 docs 11 §9)
                log.error("[failover] 无同区健康线路机可迁, 订阅留原机告警: sub={} member={} region={} faulted={} reason={}",
                        sub.getId(), sub.getMemberUserId(), plan.getRegionCode(), faultedServer, reason);
                continue;
            }
            // 只换线路机, 落地机不变; 远端由 agent 对账两端收敛
            tradeSubscriptionCertificateService.setAllocation(cert.getId(), target, cert.getIpId());
            eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(
                    sub.getId(), sub.getMemberUserId(), TradeSubscriptionChangeTypeEnum.FRONTLINE,
                    faultedServer, target, chgReason, "system-failover"));
            moved++;
            log.warn("[failover] 迁移接入点 sub={} cert={} 线路机 {} → {} reason={}",
                    sub.getId(), cert.getId(), faultedServer, target, reason);
        }
        if (moved > 0) {
            log.info("[failover] 线路机 {} 疏散: reason={} 迁移={}/{}", faultedServer, reason, moved, certs.size());
        }
    }
}
