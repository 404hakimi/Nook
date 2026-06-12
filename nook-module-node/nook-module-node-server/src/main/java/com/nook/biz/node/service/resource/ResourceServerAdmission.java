package com.nook.biz.node.service.resource;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.node.convert.resource.ResourceServerLandingConvert;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.entity.ResourceServerRuntimeDO;
import com.nook.biz.node.entity.ResourceServerTrafficDO;
import com.nook.biz.node.mapper.Socks5InstallMapper;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.mapper.ResourceServerQuotaMapper;
import com.nook.biz.node.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.mapper.ResourceServerTrafficMapper;
import com.nook.biz.node.service.rules.ResourceServerRules;
import com.nook.biz.trade.api.SubscriptionCertApi;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务器选址 + 准入核心.
 *
 * <p>一处收口三类决策, 其它 service 只调用、不重复实现:
 * 准入判定 (生命周期 + 流量限流 + 心跳 + 套餐预算/带宽达标) / 按套餐选候选落地机 / 算套餐落地机库存.
 *
 * @author nook
 */
@Component
public class ResourceServerAdmission {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private Socks5InstallMapper socks5InstallMapper;
    @Resource
    private ResourceServerQuotaMapper resourceServerQuotaMapper;
    @Resource
    private ResourceServerTrafficMapper resourceServerTrafficMapper;
    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    @Resource
    private SubscriptionCertApi subscriptionCertApi;

    /**
     * 从候选服务器中筛出健康可分配的子集
     *
     * <p>排除未上线、流量到顶限流、心跳不健康的服务器.
     *
     * @param serverIds 候选服务器ID集合
     * @return 可分配的服务器ID子集
     */
    public Set<String> filterAllocatable(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Set.of();
        }
        Map<String, ResourceServerTrafficDO> trafficMap = CollectionUtils.convertMap(
                resourceServerTrafficMapper.selectCurrentByServerIds(serverIds), ResourceServerTrafficDO::getServerId);
        return this.filterAllocatable(serverIds, trafficMap);
    }

    /** 同上, 复用调用方已查的当周期 traffic (选址路径上预算判定本就捞过同批), 省一次重复查. */
    private Set<String> filterAllocatable(Collection<String> serverIds, Map<String, ResourceServerTrafficDO> trafficMap) {
        if (CollUtil.isEmpty(serverIds)) {
            return Set.of();
        }
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);
        Map<String, ResourceServerRuntimeDO> rtMap = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
        LocalDateTime now = LocalDateTime.now();
        Set<String> allocatable = new HashSet<>(serverIds.size());
        for (String id : serverIds) {
            ResourceServerDO srv = serverMap.get(id);
            ResourceServerTrafficDO traffic = trafficMap.get(id);
            ResourceServerRuntimeDO rt = rtMap.get(id);
            if (this.isAllocatable(
                    ObjectUtil.isNull(srv) ? null : srv.getLifecycleState(),
                    ObjectUtil.isNull(traffic) ? null : traffic.getThrottleState(),
                    ObjectUtil.isNull(rt) ? null : rt.getLastHeartbeatAt(),
                    now)) {
                allocatable.add(id);
            }
        }
        return allocatable;
    }

    /** 单台服务器准入判定: 运行中 + 未触发限流 + 心跳健康 (值入参, 不依赖 DO). */
    public boolean isAllocatable(String lifecycleState, String throttleState,
                                 LocalDateTime lastHeartbeatAt, LocalDateTime now) {
        if (!ResourceServerLifecycleEnum.LIVE.matches(lifecycleState)) {
            return false;
        }
        if (ResourceServerRules.isThrottled(throttleState)) {
            return false;
        }
        Long elapsedSec = ObjectUtil.isNull(lastHeartbeatAt) ? null
                : Duration.between(lastHeartbeatAt, now).getSeconds();
        AgentOnlineState state = AgentOnlineState.classify(elapsedSec);
        return state == AgentOnlineState.ONLINE || state == AgentOnlineState.WARN;
    }

    /**
     * 查匹配套餐的运行中落地机 (同区域 + 同 IP 类型 + 健康可分配 + 预算/带宽达标)
     *
     * @param region           区域码
     * @param ipTypeId         IP 类型编号
     * @param minTrafficGb     套餐月流量 (落地机本月剩余预算须撑得起)
     * @param minBandwidthMbps 套餐带宽 (落地机带宽须 ≥)
     * @return 匹配的落地机概要
     */
    public List<LandingSummaryDTO> findMatchingForPlan(String region, String ipTypeId,
                                                       int minTrafficGb, int minBandwidthMbps) {
        if (StrUtil.isBlank(region) || StrUtil.isBlank(ipTypeId)) {
            return List.of();
        }
        // ① 区域 + 运行中 + 落地角色: 主表先筛, 把范围缩到本机房在线落地机 (区域选择性最强)
        List<ResourceServerDO> liveServers = resourceServerMapper.selectLiveLandingsByRegion(region);
        if (CollUtil.isEmpty(liveServers)) {
            return List.of();
        }
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(liveServers, ResourceServerDO::getId);
        // ② IP 类型: 只在上面这批机器里筛对应 IP 类型的落地子表
        List<Socks5InstallDO> landings =
                socks5InstallMapper.selectByServerIdsAndIpType(serverMap.keySet(), ipTypeId);
        if (CollUtil.isEmpty(landings)) {
            return List.of();
        }
        // ③ 容量 + 当周期用量子表 + 健康准入 + 预算/带宽达标 → 候选概要
        Set<String> landingServerIds = CollectionUtils.convertSet(landings, Socks5InstallDO::getServerId);
        Map<String, ResourceServerQuotaDO> capMap = CollectionUtils.convertMap(
                resourceServerQuotaMapper.selectBatchIds(landingServerIds), ResourceServerQuotaDO::getServerId);
        // 当周期 traffic 查一次, 既判健康(filterAllocatable)又判预算(meetsPlanBudget); 按全量在线落地机捞, 覆盖候选子集
        Map<String, ResourceServerTrafficDO> trafficMap = CollectionUtils.convertMap(
                resourceServerTrafficMapper.selectCurrentByServerIds(serverMap.keySet()), ResourceServerTrafficDO::getServerId);
        Set<String> allocatable = this.filterAllocatable(serverMap.keySet(), trafficMap);
        List<LandingSummaryDTO> matched = new ArrayList<>();
        for (Socks5InstallDO landing : landings) {
            if (!allocatable.contains(landing.getServerId())
                    || !this.meetsPlanBudget(capMap.get(landing.getServerId()), trafficMap.get(landing.getServerId()),
                            minTrafficGb, minBandwidthMbps)) {
                continue;
            }
            matched.add(ResourceServerLandingConvert.INSTANCE.toSummary(serverMap.get(landing.getServerId()), landing));
        }
        return matched;
    }

    /**
     * 批量算套餐落地机池库存 (各规格匹配后按占用状态分桶)
     *
     * @param specs 套餐规格集合
     * @return 套餐ID → 库存 (总数 / 可用 / 占用)
     */
    public Map<String, PlanCapacityDTO> countCapacityForPlans(Collection<PlanSpecDTO> specs) {
        if (CollUtil.isEmpty(specs)) {
            return Map.of();
        }
        // ① 批量捞 (固定查询数, 不随套餐数增长): 涉及区域的运行中落地机 → 这批机器里涉及 IP 类型的落地子表 → 容量 + 当周期用量子表
        Set<String> regions = CollectionUtils.convertSet(specs, PlanSpecDTO::getRegionCode);
        Set<String> ipTypeIds = CollectionUtils.convertSet(specs, PlanSpecDTO::getIpTypeId);
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectLiveLandingsByRegions(regions), ResourceServerDO::getId);
        List<Socks5InstallDO> landings = MapUtil.isEmpty(serverMap) ? List.of()
                : socks5InstallMapper.selectByServerIdsAndIpTypes(serverMap.keySet(), ipTypeIds);
        Set<String> landingServerIds = CollectionUtils.convertSet(landings, Socks5InstallDO::getServerId);
        Map<String, ResourceServerQuotaDO> capMap = CollUtil.isEmpty(landingServerIds) ? Map.of()
                : CollectionUtils.convertMap(resourceServerQuotaMapper.selectBatchIds(landingServerIds),
                        ResourceServerQuotaDO::getServerId);
        // 当周期 traffic 查一次, 既判健康(filterAllocatable)又判预算(meetsPlanBudget); 按全量在线落地机捞, 覆盖候选子集
        Map<String, ResourceServerTrafficDO> trafficMap = CollectionUtils.convertMap(
                resourceServerTrafficMapper.selectCurrentByServerIds(serverMap.keySet()), ResourceServerTrafficDO::getServerId);
        Set<String> allocatable = this.filterAllocatable(serverMap.keySet(), trafficMap);
        // cert.ip_id 派生占用 (含 ACTIVE/SUSPENDED); 落地机占用真相收口到凭证, 不再读 landing.status
        Set<String> boundIds = subscriptionCertApi.filterBoundIpIds(landingServerIds);
        // ② 落地机按 (区域 + IP 类型) 分桶, 让每个套餐 O(1) 命中自己的候选
        Map<String, List<Socks5InstallDO>> bucket = new HashMap<>();
        for (Socks5InstallDO landing : landings) {
            ResourceServerDO server = serverMap.get(landing.getServerId());
            bucket.computeIfAbsent(this.regionIpKey(server.getRegion(), landing.getIpTypeId()),
                    k -> new ArrayList<>()).add(landing);
        }
        // ③ 每个套餐在自己的桶里按容量阈值过滤 + status 计数 (纯内存, 不再查库)
        Map<String, PlanCapacityDTO> result = new HashMap<>(specs.size());
        for (PlanSpecDTO spec : specs) {
            List<Socks5InstallDO> candidates = bucket.getOrDefault(
                    this.regionIpKey(spec.getRegionCode(), spec.getIpTypeId()), List.of());
            int total = 0;
            int available = 0;
            int occupied = 0;
            for (Socks5InstallDO landing : candidates) {
                // 占用中的落地机在服务本套餐订阅, 即便被降配到不达标也照常计入 (运行时按 min(套餐,落地机) 带宽限速)
                if (boundIds.contains(landing.getServerId())) {
                    total++;
                    occupied++;
                    continue;
                }
                // 空闲机器需健康可分配 + 本月剩余预算/带宽达标 才算"可售"
                if (!allocatable.contains(landing.getServerId())
                        || !this.meetsPlanBudget(capMap.get(landing.getServerId()), trafficMap.get(landing.getServerId()),
                                spec.getTrafficGb(), spec.getBandwidthMbps())) {
                    continue;
                }
                total++;
                available++;
            }
            result.put(spec.getPlanId(), new PlanCapacityDTO(total, available, occupied));
        }
        return result;
    }

    /**
     * 查运行中线路机里需要故障切换的 (已触发限流或掉线)
     *
     * <p>短时心跳抖动不触发迁移, 只暂停分配新客户.
     *
     * @return 服务器ID → 切换原因 (已触发限流 / 掉线)
     */
    public Map<String, String> findFrontlinesNeedingFailover() {
        List<ResourceServerDO> frontlines = resourceServerMapper.selectLiveFrontlines();
        if (CollUtil.isEmpty(frontlines)) {
            return Map.of();
        }
        Set<String> ids = CollectionUtils.convertSet(frontlines, ResourceServerDO::getId);
        Map<String, ResourceServerTrafficDO> trafficMap = CollectionUtils.convertMap(
                resourceServerTrafficMapper.selectCurrentByServerIds(ids), ResourceServerTrafficDO::getServerId);
        Map<String, ResourceServerRuntimeDO> rtMap = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(ids), ResourceServerRuntimeDO::getServerId);
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> result = new HashMap<>();
        for (ResourceServerDO srv : frontlines) {
            ResourceServerTrafficDO traffic = trafficMap.get(srv.getId());
            if (ResourceServerRules.isThrottled(ObjectUtil.isNull(traffic) ? null : traffic.getThrottleState())) {
                result.put(srv.getId(), ResourceServerThrottleStateEnum.THROTTLED.getState());
                continue;
            }
            ResourceServerRuntimeDO rt = rtMap.get(srv.getId());
            Long elapsedSec = (ObjectUtil.isNull(rt) || ObjectUtil.isNull(rt.getLastHeartbeatAt())) ? null
                    : Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
            if (AgentOnlineState.classify(elapsedSec) == AgentOnlineState.OFFLINE) {
                result.put(srv.getId(), AgentOnlineState.OFFLINE.name());
            }
        }
        return result;
    }

    /** 空闲落地机能否再接一个套餐: 本月剩余预算撑得起 + 带宽达标 (quota / traffic 行可空, 规则在 Rules). */
    private boolean meetsPlanBudget(ResourceServerQuotaDO quota, ResourceServerTrafficDO traffic,
                                    int planTrafficGb, int planBandwidthMbps) {
        Integer totalGb = ObjectUtil.isNull(quota) ? null : quota.getTotalGb();
        Integer usablePercent = ObjectUtil.isNull(quota) ? null : quota.getUsablePercent();
        Integer bandwidthMbps = ObjectUtil.isNull(quota) ? null : quota.getBandwidthMbps();
        Long usedBytes = ObjectUtil.isNull(traffic) ? null : traffic.getUsedBytes();
        return ResourceServerRules.hasTrafficBudget(totalGb, usablePercent, usedBytes, planTrafficGb)
                && ResourceServerRules.meetsBandwidthSpec(bandwidthMbps, planBandwidthMbps);
    }

    /** (区域 + IP 类型) 复合 key; 把落地机按套餐匹配维度分桶. */
    private String regionIpKey(String region, String ipTypeId) {
        return region + '|' + ipTypeId;
    }
}
