package com.nook.biz.trade.job;

import com.nook.biz.node.api.xray.XrayClientNodeApi;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订阅生命周期 Job: 到期释放 + 流量耗尽停服.
 *
 * <p>只写 DB / 调 node 原语, 远端断连交给 reconcile (frontline 按 RUNNING 过滤移除 user/rule/outbound,
 * 落地机无 RUNNING client → tc 自动清零):
 * <ul>
 *   <li>到期 (now ≥ expiresAt): revoke (删 client + 释放落地机回池), 订阅置 EXPIRED;</li>
 *   <li>流量耗尽 (已用 ≥ 套餐 trafficGb, 周期未到): stop (置 STOPPED, 保留 IP + 记录), 订阅置 SUSPENDED.</li>
 * </ul>
 *
 * @author nook
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeLifecycleJob {

    private static final long GB = 1024L * 1024 * 1024;

    private final TradeSubscriptionMapper subMapper;
    private final TradePlanMapper planMapper;
    private final XrayClientProvisionApi provisionApi;
    private final XrayClientNodeApi clientNodeApi;

    @Scheduled(cron = "${nook.trade.lifecycle-cron:15 * * * * ?}")
    public void check() {
        List<TradeSubscriptionDO> active = subMapper.selectAllActive();
        if (active.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        Set<String> planIds = active.stream()
                .map(TradeSubscriptionDO::getPlanId).collect(Collectors.toSet());
        Map<String, Integer> planTrafficGb = planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(TradePlanDO::getId,
                        p -> p.getTrafficGb() == null ? 0 : p.getTrafficGb()));
        Set<String> clientIds = active.stream()
                .map(TradeSubscriptionDO::getXrayClientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Long> usedBytes = clientNodeApi.getUsedBytesByClientIds(clientIds);

        int expired = 0;
        int suspended = 0;
        for (TradeSubscriptionDO s : active) {
            try {
                // 1. 到期 → 释放 (revoke); 到期是确定事实, revoke 失败 (client 已不存在) 也照样标 EXPIRED
                if (s.getExpiresAt() != null && !s.getExpiresAt().isAfter(now)) {
                    try {
                        provisionApi.revoke(s.getXrayClientId());
                    } catch (RuntimeException re) {
                        log.warn("[lifecycle] 到期 revoke 失败 (client 可能已不存在) sub={}: {}",
                                s.getId(), re.getMessage());
                    }
                    s.setStatus(TradeSubscriptionStatusEnum.EXPIRED.getState());
                    subMapper.updateById(s);
                    expired++;
                    continue;
                }
                // 2. 流量耗尽 → 停服保留 IP (stop)
                int gb = planTrafficGb.getOrDefault(s.getPlanId(), 0);
                if (gb > 0) {
                    long used = usedBytes.getOrDefault(s.getXrayClientId(), 0L);
                    if (used >= (long) gb * GB) {
                        provisionApi.stop(s.getXrayClientId());
                        s.setStatus(TradeSubscriptionStatusEnum.SUSPENDED.getState());
                        subMapper.updateById(s);
                        suspended++;
                    }
                }
            } catch (Exception e) {
                log.error("[lifecycle] sub={} client={} 处理失败: {}",
                        s.getId(), s.getXrayClientId(), e.getMessage(), e);
            }
        }
        if (expired + suspended > 0) {
            log.info("[lifecycle] 扫描完成: active={} 到期释放={} 流量耗尽停服={}",
                    active.size(), expired, suspended);
        }
    }
}
