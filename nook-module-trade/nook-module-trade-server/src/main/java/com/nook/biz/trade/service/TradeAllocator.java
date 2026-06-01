package com.nook.biz.trade.service;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.xray.XrayClientNodeApi;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

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
    private ResourceServerCapacityApi capacityApi;
    @Resource
    private XrayClientProvisionApi provisionApi;
    @Resource
    private XrayClientNodeApi clientNodeApi;
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
        Map<String, ResourceServerCapacityRespDTO> capMap = capacityApi.listByServerIds(fIds);
        Map<String, Integer> committed = committedBandwidthByFrontline(fIds);
        Map<String, Integer> clientCounts = provisionApi.countActiveByServerIds(fIds);

        String best = null;
        int bestHeadroom = Integer.MIN_VALUE;
        for (ResourceServerRespDTO f : frontlines) {
            ResourceServerCapacityRespDTO cap = capMap.get(f.getId());
            // 流量到顶(THROTTLED)的线路机不再接新订阅
            if (cap != null && ResourceServerThrottleStateEnum.THROTTLED.matches(cap.getThrottleState())) {
                continue;
            }
            Integer bwLimit = cap == null ? null : cap.getBandwidthLimitMbps();
            // 不超卖语义: 线路机须配带宽上限才能参与准入
            if (bwLimit == null || bwLimit <= 0) {
                continue;
            }
            // 客户数硬上限 (二级保险; 0=不限)
            Integer maxClients = cap.getClientMaxCount();
            if (maxClients != null && maxClients > 0
                    && clientCounts.getOrDefault(f.getId(), 0) >= maxClients) {
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
     * 选同区域 + 同 IP 类型 + 规格达标的 AVAILABLE 落地机 (排除已试过的); 无候选返 null.
     *
     * @param region   区域码
     * @param ipTypeId IP 类型
     * @param minTrafficGb     套餐月流量
     * @param minBandwidthMbps 套餐带宽
     * @param exclude  已试过的落地机 id
     */
    public String pickLanding(String region, String ipTypeId, int minTrafficGb,
                              int minBandwidthMbps, Set<String> exclude) {
        return landingApi.findMatchingForPlan(region, ipTypeId, minTrafficGb, minBandwidthMbps).stream()
                .filter(l -> ResourceServerLandingStatusEnum.AVAILABLE.matches(l.getStatus())
                        && !exclude.contains(l.getServerId()))
                .map(LandingSummaryDTO::getServerId)
                .findFirst()
                .orElse(null);
    }

    /** 各线路机当前已挂带宽 = Σ(经过它的 ACTIVE 订阅的套餐带宽). */
    private Map<String, Integer> committedBandwidthByFrontline(Set<String> frontlineIds) {
        // 只捞这批线路机上的 client (不全量扫订阅), 再回查这些 client 的生效订阅
        Map<String, String> clientToServer = clientNodeApi.getClientServerMapByServerIds(frontlineIds);
        if (clientToServer.isEmpty()) {
            return Map.of();
        }
        List<TradeSubscriptionDO> active = subMapper.selectActiveByClientIds(clientToServer.keySet());
        if (CollUtil.isEmpty(active)) {
            return Map.of();
        }
        Set<String> planIds = CollectionUtils.convertSet(active, TradeSubscriptionDO::getPlanId);
        Map<String, Integer> planBw = CollectionUtils.convertMap(
                planMapper.selectBatchIds(planIds), TradePlanDO::getId,
                p -> p.getBandwidthMbps() == null ? 0 : p.getBandwidthMbps());
        Map<String, Integer> committed = new HashMap<>();
        for (TradeSubscriptionDO s : active) {
            String fId = clientToServer.get(s.getXrayClientId());
            if (fId == null) {
                continue;
            }
            committed.merge(fId, planBw.getOrDefault(s.getPlanId(), 0), Integer::sum);
        }
        return committed;
    }
}
