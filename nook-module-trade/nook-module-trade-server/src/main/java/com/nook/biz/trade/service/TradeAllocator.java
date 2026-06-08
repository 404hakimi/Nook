package com.nook.biz.trade.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerQuotaApi;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerQuotaRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源分配器: 按区域自动匹配落地机 + 线路机 (只选址)
 *
 * @author nook
 */
@Component
public class TradeAllocator {

    /** 带宽预留率: 不超卖, 线路机留 10% 余量. */
    private static final double RESERVE_RATIO = 0.10;

    @Resource
    private ResourceServerApi serverApi;
    @Resource
    private ResourceServerLandingApi landingApi;
    @Resource
    private ResourceServerQuotaApi resourceServerQuotaApi;
    @Resource
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;
    @Resource
    private TradeSubscriptionMapper subMapper;
    @Resource
    private TradePlanMapper planMapper;

    /**
     * 选同区域、带宽准入通过、剩余带宽最多的 LIVE 线路机; 无候选返 null.
     *
     * @param region            区域码
     * @param planBandwidthMbps 套餐带宽 (要占用的)
     */
    public String pickFrontline(String region, int planBandwidthMbps) {
        List<ResourceServerRespDTO> frontlines = serverApi.findLiveFrontlinesByRegion(region);
        if (CollUtil.isEmpty(frontlines)) {
            return null;
        }
        Set<String> fIds = CollectionUtils.convertSet(frontlines, ResourceServerRespDTO::getId);
        // 健康准入: 非 LIVE / 到顶 / 心跳不健康 一处判定 (node 侧 ResourceServerAdmission)
        Set<String> allocatable = serverApi.filterAllocatable(fIds);
        Map<String, ResourceServerQuotaRespDTO> capMap = resourceServerQuotaApi.listByServerIds(fIds);
        Map<String, Integer> committed = committedBandwidthByFrontline(fIds);

        String best = null;
        int bestHeadroom = Integer.MIN_VALUE;
        for (ResourceServerRespDTO f : frontlines) {
            if (!allocatable.contains(f.getId())) {
                continue;
            }
            ResourceServerQuotaRespDTO cap = capMap.get(f.getId());
            Integer bwLimit = cap == null ? null : cap.getBandwidthMbps();
            // 不超卖语义: 线路机须配带宽上限才能参与准入
            if (bwLimit == null || bwLimit <= 0) {
                continue;
            }
            int allowed = (int) Math.floor(bwLimit * (1 - RESERVE_RATIO));
            int headroom = allowed - committed.getOrDefault(f.getId(), 0) - planBandwidthMbps;
            if (headroom < 0) {
                continue;
            }
            if (headroom > bestHeadroom) {
                bestHeadroom = headroom;
                best = f.getId();
            }
        }
        return best;
    }

    /**
     * 选同区域、同IP类型、并且规格达标 可分配的落地机候选
     *
     * @param region           区域码
     * @param ipTypeId         IP 类型
     * @param minTrafficGb     套餐月流量
     * @param minBandwidthMbps 套餐带宽
     * @return 候选落地机 id 列表 (上层逐台试占, 被并发抢占则换下一台)
     */
    public List<String> matchLandings(String region, String ipTypeId, int minTrafficGb, int minBandwidthMbps) {
        List<String> matching = landingApi.findMatchingForPlan(region, ipTypeId, minTrafficGb, minBandwidthMbps).stream()
                .map(LandingSummaryDTO::getServerId)
                .toList();
        // 占用收口到 cert.ip_id: 去掉已被生效凭证绑定的, 候选只留真正空闲 (逐台试占由 uk_cert_ip 唯一键兜底)
        Set<String> bound = tradeSubscriptionCertificateService.filterBoundIpIds(matching);
        return matching.stream().filter(id -> !bound.contains(id)).toList();
    }

    /** 各线路机当前已挂带宽 = Σ(挂在它上面的应运行凭证, 其订阅套餐带宽). */
    private Map<String, Integer> committedBandwidthByFrontline(Set<String> frontlineIds) {
        // 只捞这批线路机上应运行的凭证 (不全量扫订阅), 再回查其订阅 + 套餐带宽
        List<TradeSubscriptionCertificateDO> certs = new ArrayList<>();
        for (String frontlineId : frontlineIds) {
            certs.addAll(tradeSubscriptionCertificateService.listActiveByServer(frontlineId));
        }
        if (CollUtil.isEmpty(certs)) {
            return Map.of();
        }
        Map<String, TradeSubscriptionDO> subMap = CollectionUtils.convertMap(
                subMapper.selectBatchIds(CollectionUtils.convertSet(certs, TradeSubscriptionCertificateDO::getSubscriptionId)),
                TradeSubscriptionDO::getId);
        Map<String, Integer> planBw = CollectionUtils.convertMap(
                planMapper.selectBatchIds(CollectionUtils.convertSet(subMap.values(), TradeSubscriptionDO::getPlanId)),
                TradePlanDO::getId, p -> ObjectUtil.isNull(p.getBandwidthMbps()) ? 0 : p.getBandwidthMbps());
        Map<String, Integer> committed = new HashMap<>();
        for (TradeSubscriptionCertificateDO cert : certs) {
            TradeSubscriptionDO sub = subMap.get(cert.getSubscriptionId());
            if (ObjectUtil.isNull(sub)) {
                continue;
            }
            committed.merge(cert.getServerId(), planBw.getOrDefault(sub.getPlanId(), 0), Integer::sum);
        }
        return committed;
    }
}
