package com.nook.biz.trade.job;

import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.xray.XrayClientNodeApi;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.MemberPlanTrafficDO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.MemberPlanTrafficMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
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
 *   <li>流量耗尽 (已用 ≥ 套餐 trafficGb): stop (置 STOPPED, 保留 IP + 记录), 订阅置 SUSPENDED.</li>
 * </ul>
 *
 * <p>用量按订阅累加进 member_plan_traffic, 源是该订阅独占落地机的 NIC tx (1:1 ⟹ 机器流量=该用户流量;
 * 用 tx 单向, rx+tx 是中转 2×). used_bytes 是 DB 强一致权威; 落地机 tx 是当周期值 (月度会归零),
 * 累加规则: 换落地机重基线 / 计数回退当机器侧重置(只挪游标不动 used) / 正常累加. 线路机切换不影响
 * (落地机没变); VPS 重建 / vnstat 清都走"回退即重置"分支, used_bytes 一字节不丢.
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
    private final MemberPlanTrafficMapper trafficMapper;
    private final XrayClientProvisionApi provisionApi;
    private final XrayClientNodeApi clientNodeApi;
    private final ResourceServerCapacityApi capacityApi;

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
        // clientId → 落地机 ip_id, 再取落地机当周期 tx
        Map<String, String> landingByClient = clientNodeApi.getLandingIdByClientIds(clientIds);
        Set<String> landingIds = new HashSet<>(landingByClient.values());
        Map<String, ResourceServerCapacityRespDTO> capMap = landingIds.isEmpty()
                ? Map.of() : capacityApi.listByServerIds(landingIds);

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
                // 2. 落地机 tx 累加进 member_plan_traffic; 用满套餐流量 → 停服保留 IP
                if (accumulateAndMaybeSuspend(s, now, landingByClient, capMap, planTrafficGb)) {
                    suspended++;
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

    /**
     * 把落地机 tx 增量累加进订阅用量; 达套餐流量上限则停服. 返回是否本轮停服.
     *
     * <p>累加规则 (DB used_bytes 为权威, lastCounterTx 仅游标):
     * <ul>
     *   <li>首见: used=0, 基线=当前 (不把落地机历史余量算进来);</li>
     *   <li>换落地机 (换地区/IP): 重打基线, used 保留;</li>
     *   <li>计数回退 (月度归零/vnstat 清/VPS 重建): 只挪基线, used 不动;</li>
     *   <li>正常: used += 当前 - 上次.</li>
     * </ul>
     */
    private boolean accumulateAndMaybeSuspend(TradeSubscriptionDO s, LocalDateTime now,
                                              Map<String, String> landingByClient,
                                              Map<String, ResourceServerCapacityRespDTO> capMap,
                                              Map<String, Integer> planTrafficGb) {
        String clientId = s.getXrayClientId();
        String landingId = landingByClient.get(clientId);
        if (landingId == null) {
            return false; // 无落地机绑定信息, 跳过本轮
        }
        ResourceServerCapacityRespDTO cap = capMap.get(landingId);
        if (cap == null || cap.getTxBytes() == null) {
            return false; // 落地机还没上报 NIC, 本轮无数据
        }
        long cur = cap.getTxBytes();

        MemberPlanTrafficDO row = trafficMapper.selectById(s.getId());
        if (row == null) {
            row = new MemberPlanTrafficDO();
            row.setSubscriptionId(s.getId());
            row.setMemberUserId(s.getMemberUserId());
            row.setLandingServerId(landingId);
            row.setUsedBytes(0L);
            row.setLastCounterTx(cur); // 首见: 基线=当前, used=0 (不继承落地机历史)
            row.setLastSampledAt(now);
            trafficMapper.insert(row);
        } else {
            long used = row.getUsedBytes() == null ? 0L : row.getUsedBytes();
            long last = row.getLastCounterTx() == null ? cur : row.getLastCounterTx();
            if (!landingId.equals(row.getLandingServerId())) {
                row.setLandingServerId(landingId); // ① 换落地机: 重基线, used 保留
                row.setLastCounterTx(cur);
            } else if (cur < last) {
                row.setLastCounterTx(cur);         // ② 计数回退: 只挪游标, used 不动
            } else {
                row.setUsedBytes(used + (cur - last)); // ③ 正常累加
                row.setLastCounterTx(cur);
            }
            // 周期重置 (cycleResetAt 非空且已到); 当前窗口=订阅生命周期, cycleResetAt 留空不触发
            if (row.getCycleResetAt() != null && !now.isBefore(row.getCycleResetAt())) {
                row.setUsedBytes(0L);
                row.setCycleResetAt(null);
            }
            row.setLastSampledAt(now);
            trafficMapper.updateById(row);
        }

        int gb = planTrafficGb.getOrDefault(s.getPlanId(), 0);
        long usedNow = row.getUsedBytes() == null ? 0L : row.getUsedBytes();
        if (gb > 0 && usedNow >= (long) gb * GB) {
            provisionApi.stop(clientId);
            s.setStatus(TradeSubscriptionStatusEnum.SUSPENDED.getState());
            subMapper.updateById(s);
            return true;
        }
        return false;
    }
}
