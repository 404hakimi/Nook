package com.nook.biz.trade.job;

import cn.hutool.core.collection.CollUtil;
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
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订阅生命周期 Job: 到期释放 + 流量耗尽停服
 *
 * @author nook
 */
@Slf4j
@Component
public class TradeLifecycleJob {

    private static final long GB = 1024L * 1024 * 1024;
    /** 流量重置周期(天); 默认 30 天一个周期, 从开通时刻起算(保留时分秒). */
    private static final int CYCLE_RESET_DAYS = 30;

    @Resource
    private TradeSubscriptionMapper subMapper;
    @Resource
    private TradePlanMapper planMapper;
    @Resource
    private MemberPlanTrafficMapper trafficMapper;
    @Resource
    private XrayClientProvisionApi provisionApi;
    @Resource
    private XrayClientNodeApi clientNodeApi;
    @Resource
    private ResourceServerCapacityApi capacityApi;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Scheduled(cron = "#{@tradeJobProperties.lifecycleCron}")
    public void check() {
        List<TradeSubscriptionDO> active = subMapper.selectActiveOrSuspended(); // 含停服(到重置点要恢复)
        if (CollUtil.isEmpty(active)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        Set<String> planIds = CollectionUtils.convertSet(active, TradeSubscriptionDO::getPlanId);
        Map<String, Integer> planTrafficGb = CollectionUtils.convertMap(
                planMapper.selectBatchIds(planIds), TradePlanDO::getId,
                p -> p.getTrafficGb() == null ? 0 : p.getTrafficGb());
        Set<String> clientIds = CollectionUtils.convertSet(
                active, TradeSubscriptionDO::getXrayClientId, s -> s.getXrayClientId() != null);
        // clientId → 落地机 ip_id, 再取落地机当周期 tx
        Map<String, String> landingByClient = clientNodeApi.getLandingIdByClientIds(clientIds);
        Set<String> landingIds = new HashSet<>(landingByClient.values());
        Map<String, ResourceServerCapacityRespDTO> capMap = CollUtil.isEmpty(landingIds)
                ? Map.of() : capacityApi.listByServerIds(landingIds);
        // 订阅用量行: 循环前一次批量载入, 避免逐订阅 selectById (N+1)
        Map<String, MemberPlanTrafficDO> trafficBySub = CollectionUtils.convertMap(
                trafficMapper.selectBatchIds(CollectionUtils.convertSet(active, TradeSubscriptionDO::getId)),
                MemberPlanTrafficDO::getSubscriptionId);

        int expired = 0;
        int suspended = 0;
        int resumed = 0;
        for (TradeSubscriptionDO s : active) {
            try {
                // 到期 → 释放 (revoke); 到期是确定事实, revoke 失败 (client 已不存在) 也照样标 EXPIRED
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
                // 停服订阅: 到重置点则恢复 (清零 + 重打基线 + 复活 client); 没到则继续停, 不累加
                if (TradeSubscriptionStatusEnum.SUSPENDED.matches(s.getStatus())) {
                    Boolean resumedNow = transactionTemplate.execute(st ->
                            resumeIfCycleReset(s, now, landingByClient, capMap, trafficBySub));
                    if (Boolean.TRUE.equals(resumedNow)) {
                        resumed++;
                    }
                    continue;
                }
                // ACTIVE: 落地机业务流量累加进 member_plan_traffic; 用满套餐流量 → 停服保留 IP
                // 单订阅一个事务: 累加 + stop + 标 SUSPENDED 原子提交 (private 方法靠 TransactionTemplate, 非 @Transactional self-invocation)
                Boolean suspendedNow = transactionTemplate.execute(st ->
                        accumulateAndMaybeSuspend(s, now, landingByClient, capMap, planTrafficGb, trafficBySub));
                if (Boolean.TRUE.equals(suspendedNow)) {
                    suspended++;
                }
            } catch (Exception e) {
                log.error("[lifecycle] sub={} client={} 处理失败: {}",
                        s.getId(), s.getXrayClientId(), e.getMessage(), e);
            }
        }
        if (expired + suspended + resumed > 0) {
            log.info("[lifecycle] 扫描完成: 总={} 到期释放={} 流量耗尽停服={} 重置恢复={}",
                    active.size(), expired, suspended, resumed);
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
                                              Map<String, Integer> planTrafficGb,
                                              Map<String, MemberPlanTrafficDO> trafficBySub) {
        String clientId = s.getXrayClientId();
        String landingId = landingByClient.get(clientId);
        if (landingId == null) {
            return false; // 无落地机绑定信息, 跳过本轮
        }
        ResourceServerCapacityRespDTO cap = capMap.get(landingId);
        if (cap == null) {
            return false;
        }
        // 优先用 socks5 业务流量(已排除 agent/系统); 老 agent 未上报 biz 时回退整机 tx
        Long cumSource = cap.getBizUsedBytes() != null ? cap.getBizUsedBytes() : cap.getTxBytes();
        if (cumSource == null) {
            return false; // 落地机还没上报, 本轮无数据
        }
        long cur = cumSource;

        MemberPlanTrafficDO row = trafficBySub.get(s.getId());
        if (row == null) {
            row = new MemberPlanTrafficDO();
            row.setSubscriptionId(s.getId());
            row.setMemberUserId(s.getMemberUserId());
            row.setLandingServerId(landingId);
            row.setUsedBytes(0L);
            row.setLastCounterTx(cur); // 首见: 基线=当前, used=0 (不继承落地机历史)
            row.setCycleResetAt(firstCycleReset(s)); // 流量重置锚点=开通时刻+30天; 不足一周期返 null
            row.setLastSampledAt(now);
            trafficMapper.insert(row);
        } else {
            long used = row.getUsedBytes() == null ? 0L : row.getUsedBytes();
            long last = row.getLastCounterTx() == null ? cur : row.getLastCounterTx();
            if (!landingId.equals(row.getLandingServerId())) {
                row.setLandingServerId(landingId); // 换落地机: 重基线, used 保留
                row.setLastCounterTx(cur);
            } else if (cur < last) {
                row.setLastCounterTx(cur);         // 计数回退: 只挪游标, used 不动
            } else {
                row.setUsedBytes(used + (cur - last)); // 正常累加
                row.setLastCounterTx(cur);
            }
            // 流量周期重置: 到锚点(+30天)则清零 + 重打基线 + 推下一周期; 超到期不再设, 由 expiresAt 走过期
            if (row.getCycleResetAt() != null && !now.isBefore(row.getCycleResetAt())) {
                row.setUsedBytes(0L);
                row.setLastCounterTx(cur); // 重打基线: 新周期从当前累计起算, 不把上周期增量带过来
                LocalDateTime next = row.getCycleResetAt().plusDays(CYCLE_RESET_DAYS);
                row.setCycleResetAt(s.getExpiresAt() != null && next.isBefore(s.getExpiresAt()) ? next : null);
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

    /** 流量重置锚点: 开通时刻 + 30 天(保留时分秒, 如 06-02 01:21:31 → 07-02 01:21:31); 不足一个重置周期(锚点 ≥ 到期)则返 null. */
    private LocalDateTime firstCycleReset(TradeSubscriptionDO s) {
        if (s.getStartedAt() == null) {
            return null;
        }
        LocalDateTime first = s.getStartedAt().plusDays(CYCLE_RESET_DAYS);
        if (s.getExpiresAt() != null && !first.isBefore(s.getExpiresAt())) {
            return null;
        }
        return first;
    }

    /** 停服订阅到重置点: 清零 + 重打基线 + 推下周期 + 复活 (client 置 RUNNING, 订阅转 ACTIVE). 返回是否本轮恢复. */
    private boolean resumeIfCycleReset(TradeSubscriptionDO s, LocalDateTime now,
                                       Map<String, String> landingByClient,
                                       Map<String, ResourceServerCapacityRespDTO> capMap,
                                       Map<String, MemberPlanTrafficDO> trafficBySub) {
        MemberPlanTrafficDO row = trafficBySub.get(s.getId());
        if (row == null || row.getCycleResetAt() == null || now.isBefore(row.getCycleResetAt())) {
            return false; // 没到重置点, 继续停服
        }
        row.setUsedBytes(0L);
        Long cur = currentBiz(s.getXrayClientId(), landingByClient, capMap);
        if (cur != null) {
            row.setLastCounterTx(cur); // 重打基线: 恢复后从当前累计起算
        }
        LocalDateTime next = row.getCycleResetAt().plusDays(CYCLE_RESET_DAYS);
        row.setCycleResetAt(s.getExpiresAt() != null && next.isBefore(s.getExpiresAt()) ? next : null);
        row.setLastSampledAt(now);
        trafficMapper.updateById(row);
        // 复活: client 置回 RUNNING (落地机未释放, reconcile 自动装回), 订阅转 ACTIVE
        provisionApi.resume(s.getXrayClientId());
        s.setStatus(TradeSubscriptionStatusEnum.ACTIVE.getState());
        subMapper.updateById(s);
        return true;
    }

    /** 取某 client 当前落地机的业务流量累计 (biz 优先, 回退整机 tx); 无数据返 null. */
    private Long currentBiz(String clientId, Map<String, String> landingByClient,
                            Map<String, ResourceServerCapacityRespDTO> capMap) {
        String landingId = landingByClient.get(clientId);
        if (landingId == null) {
            return null;
        }
        ResourceServerCapacityRespDTO cap = capMap.get(landingId);
        if (cap == null) {
            return null;
        }
        return cap.getBizUsedBytes() != null ? cap.getBizUsedBytes() : cap.getTxBytes();
    }
}
