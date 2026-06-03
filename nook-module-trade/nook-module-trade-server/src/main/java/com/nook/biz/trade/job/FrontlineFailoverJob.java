package com.nook.biz.trade.job;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.xray.XrayClientApi;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.event.SubscriptionMachineChangeEvent;
import com.nook.biz.trade.config.TradeJobProperties;
import com.nook.biz.trade.service.TradeAllocator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 线路机故障切换 Job: 到顶 / 掉线的线路机, 把存量订阅迁到同区域健康线路机 (设计见 docs 11)
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
    private XrayClientApi xrayClientApi;
    @Resource
    private XrayClientProvisionApi provisionApi;
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

    /** 把故障线路机上的活跃订阅逐个迁到同区域健康线路机; 无目标则留原机 + 告警. */
    private void evacuate(String faultedServer, String reason) {
        // 该线路机上的 client → 其活跃订阅 (复用现有反查; 故障源后续被准入网关自动排除)
        Set<String> clientIds = xrayClientApi.getClientServerMapByServerIds(Set.of(faultedServer)).keySet();
        if (CollUtil.isEmpty(clientIds)) {
            return;
        }
        List<TradeSubscriptionDO> subs = subMapper.selectActiveByClientIds(clientIds);
        if (CollUtil.isEmpty(subs)) {
            return;
        }
        // 掉线=尽快迁完; 到顶=每轮限量(渐进, 用余量当 runway)
        boolean urgent = !ResourceServerThrottleStateEnum.THROTTLED.matches(reason);
        int budget = urgent ? subs.size()
                : Math.min(tradeJobProperties.getFailover().getThrottledBatch(), subs.size());
        TradeSubscriptionChangeReasonEnum chgReason = urgent
                ? TradeSubscriptionChangeReasonEnum.FAULT
                : TradeSubscriptionChangeReasonEnum.TRAFFIC_EXHAUSTED;

        int moved = 0;
        for (TradeSubscriptionDO s : subs) {
            if (moved >= budget) {
                break;
            }
            TradePlanDO plan = planMapper.selectById(s.getPlanId());
            if (plan == null) {
                continue;
            }
            int bw = plan.getBandwidthMbps() == null ? 0 : plan.getBandwidthMbps();
            // 复用分配策略选目标; 故障源已被统一准入网关排除(到顶/掉线), 不会被选回; 逐个迁→committed 实时变→自分散
            String target = allocator.pickFrontline(plan.getRegionCode(), bw);
            if (target == null) {
                // 无目标兜底: 留原机 + 告警 (不硬塞/不跨区, 决策见 docs 11 §9)
                log.error("[failover] 无同区健康线路机可迁, 订阅留原机告警: sub={} member={} region={} faulted={} reason={}",
                        s.getId(), s.getMemberUserId(), plan.getRegionCode(), faultedServer, reason);
                continue;
            }
            provisionApi.rebindFrontline(s.getXrayClientId(), target);
            eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(
                    s.getId(), s.getMemberUserId(), TradeSubscriptionChangeTypeEnum.FRONTLINE,
                    faultedServer, target, chgReason, "system-failover"));
            moved++;
            log.warn("[failover] 迁移订阅 sub={} 线路机 {} → {} reason={}", s.getId(), faultedServer, target, reason);
        }
        if (moved > 0) {
            log.info("[failover] 线路机 {} 疏散: reason={} 迁移={}/{}", faultedServer, reason, moved, subs.size());
        }
    }
}
