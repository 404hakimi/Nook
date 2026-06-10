package com.nook.biz.trade.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
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

import java.util.ArrayList;
import java.util.HashSet;
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

    /** 候选组目标大小: 一主两备 (决策 1); 区域不足则有几台用几台. */
    private static final int FRONTLINE_GROUP_SIZE = 3;

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

    /** 故障机退出每个受影响接入点的候选组, 组内换血补足; 主挂又补不到机则留原机 + 告警. */
    private void evacuate(String faultedServer, String reason) {
        // 候选组含该机的应运行凭证 (组口径: 故障机可能是某接入点的主或备, 都要处理)
        List<TradeSubscriptionCertificateDO> certs =
                tradeSubscriptionCertificateService.listActiveByServerInGroup(faultedServer);
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
            List<String> newGroup = this.rebuildGroup(cert, faultedServer, plan.getRegionCode(), bw);
            if (CollUtil.isEmpty(newGroup)) {
                // 主挂又一个备都没有、还补不到新机: 留原机 + 告警 (置空会让接入点彻底无入口)
                log.error("[failover] 主线路机故障且无备无补, 接入点留原机告警: sub={} member={} region={} faulted={} reason={}",
                        sub.getId(), sub.getMemberUserId(), plan.getRegionCode(), faultedServer, reason);
                continue;
            }
            String newPrimary = newGroup.get(0);
            List<String> newStandby = newGroup.subList(1, newGroup.size());
            // 主 + 备一次写回, 落地机不变; 远端由 agent 对账组内每台收敛
            tradeSubscriptionCertificateService.setFrontlineGroup(cert.getId(), newPrimary, newStandby);
            eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(
                    sub.getId(), sub.getMemberUserId(), TradeSubscriptionChangeTypeEnum.FRONTLINE,
                    faultedServer, newPrimary, chgReason, "system-failover"));
            moved++;
            log.warn("[failover] 接入点候选组换血 sub={} cert={} 移除={} 新主={} 备={} reason={}",
                    sub.getId(), cert.getId(), faultedServer, newPrimary, newStandby, reason);
        }
        if (moved > 0) {
            log.info("[failover] 线路机 {} 疏散: reason={} 换血={}/{}", faultedServer, reason, moved, certs.size());
        }
    }

    /**
     * 算出移除故障机后的新候选组 (有序, 下标 0 = 主). 主挂则提第一个备升主, 是备则删掉它,
     * 再补足到组大小, 新机入备位队尾. 主挂且无备无补返空 (调用方留原机告警).
     */
    private List<String> rebuildGroup(TradeSubscriptionCertificateDO cert, String faultedServer, String region, int bw) {
        // 当前组: 主 + 备 (备存 CSV)
        List<String> survivors = new ArrayList<>();
        boolean primaryFaulted = ObjectUtil.equal(cert.getServerId(), faultedServer);
        if (!primaryFaulted && StrUtil.isNotBlank(cert.getServerId())) {
            survivors.add(cert.getServerId());
        }
        for (String standby : this.splitCsv(cert.getStandbyServerIds())) {
            if (!ObjectUtil.equal(standby, faultedServer)) {
                survivors.add(standby);
            }
        }
        // 补足: 排除幸存者 + 故障机, 选址自身的准入网关再排到顶/掉线机
        int need = FRONTLINE_GROUP_SIZE - survivors.size();
        if (need > 0) {
            Set<String> exclude = new HashSet<>(survivors);
            exclude.add(faultedServer);
            survivors.addAll(allocator.pickFrontlines(region, bw, need, exclude));
        }
        // 主挂时第一个幸存者(原备升主或新补机)即新主; 一个都没有 = 无法换血
        return survivors;
    }

    /** 备机 CSV 拆成有序列表; 空返空列表. */
    private List<String> splitCsv(String csv) {
        return StrUtil.isBlank(csv) ? List.of() : StrUtil.split(csv, ',');
    }
}
