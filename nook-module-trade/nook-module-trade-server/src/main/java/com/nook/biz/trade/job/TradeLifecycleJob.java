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
        List<TradeSubscriptionDO> active = subMapper.selectAllActive();
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
                // 落地机 tx 累加进 member_plan_traffic; 用满套餐流量 → 停服保留 IP
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
                                              Map<String, Integer> planTrafficGb,
                                              Map<String, MemberPlanTrafficDO> trafficBySub) {
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

        MemberPlanTrafficDO row = trafficBySub.get(s.getId());
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
                row.setLandingServerId(landingId); // 换落地机: 重基线, used 保留
                row.setLastCounterTx(cur);
            } else if (cur < last) {
                row.setLastCounterTx(cur);         // 计数回退: 只挪游标, used 不动
            } else {
                row.setUsedBytes(used + (cur - last)); // 正常累加
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
