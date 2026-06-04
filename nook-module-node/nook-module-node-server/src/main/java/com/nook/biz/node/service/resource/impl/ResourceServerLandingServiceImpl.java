package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerProvisionModeEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.node.controller.resource.vo.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCapacityUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerBillingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.convert.resource.ResourceServerLandingConvert;
import com.nook.biz.node.service.resource.ResourceServerAdmission;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SOCKS5 落地节点 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerLandingServiceImpl implements ResourceServerLandingService {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerLandingMapper resourceServerLandingMapper;
    @Resource
    private ResourceServerBillingMapper resourceServerBillingMapper;
    @Resource
    private ResourceServerCapacityMapper resourceServerCapacityMapper;
    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ResourceServerLandingValidator resourceServerLandingValidator;
    @Resource
    private ResourceServerAdmission resourceServerAdmission;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initSubtables(String serverId, String ipTypeId) {
        // 只填业务必填字段; SOCKS5 凭据 / dante install 字段留 null, 由"部署"流程前端 prefill 后写入
        LocalDateTime now = LocalDateTime.now();
        ResourceServerLandingDO lan = new ResourceServerLandingDO();
        lan.setServerId(serverId);
        lan.setIpTypeId(ipTypeId);
        lan.setProvisionMode(ResourceServerProvisionModeEnum.SELF_DEPLOY.getCode());
        lan.setStatus(ResourceServerLandingStatusEnum.AVAILABLE.getState());
        lan.setAssignCount(0);
        lan.setCreatedAt(now);
        lan.setUpdatedAt(now);
        resourceServerLandingMapper.insert(lan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ResourceServerDO srv = resourceServerValidator.validateExists(id);
        resourceServerLandingValidator.validateNoBoundClient(id, srv.getIpAddress());
        resourceServerMapper.deleteById(id);
        log.info("[landing] DELETE id={} ip={}", id, srv.getIpAddress());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCore(String id, ServerLandingCoreUpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        resourceServerLandingValidator.validateExists(id);
        resourceServerLandingValidator.validateIpTypeExists(reqVO.getIpTypeId());
        resourceServerLandingValidator.validateIpAddressUnique(id, reqVO.getIpAddress());

        ResourceServerDO srvPatch = new ResourceServerDO();
        srvPatch.setId(id);
        srvPatch.setRegion(reqVO.getRegion());
        srvPatch.setIpAddress(reqVO.getIpAddress());
        srvPatch.setRemark(reqVO.getRemark());
        resourceServerMapper.updateById(srvPatch);

        ResourceServerLandingDO lanPatch = new ResourceServerLandingDO();
        lanPatch.setServerId(id);
        lanPatch.setIpTypeId(reqVO.getIpTypeId());
        lanPatch.setProvisionMode(reqVO.getProvisionMode());
        resourceServerLandingMapper.updateBySelective(lanPatch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSocks5(String id, ServerLandingSocks5UpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        resourceServerLandingValidator.validateExists(id);

        ResourceServerLandingDO patch = BeanUtils.toBean(reqVO, ResourceServerLandingDO.class);
        patch.setServerId(id);
        // 密码留空 = 保留原值
        if (StrUtil.isBlank(patch.getSocks5Password())) {
            patch.setSocks5Password(null);
        }
        resourceServerLandingMapper.updateBySelective(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBilling(String id, ServerLandingBillingUpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        LocalDateTime now = LocalDateTime.now();

        ResourceServerBillingDO old = resourceServerBillingMapper.selectById(id);
        if (ObjectUtil.isNull(old)) {
            ResourceServerBillingDO bill = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
            bill.setServerId(id);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            resourceServerBillingMapper.insert(bill);
            return;
        }
        ResourceServerBillingDO patch = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
        patch.setServerId(id);
        resourceServerBillingMapper.updateBySelective(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCapacity(String id, ServerLandingCapacityUpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        // capacity 在 create 时已 insert 占位, 这里只走 update 业务阈值
        resourceServerCapacityMapper.updateQuota(id, reqVO.getMonthlyTrafficGb(), reqVO.getBandwidthLimitMbps(),
                reqVO.getQuotaResetPolicy(), reqVO.getResetDay());
    }

    @Override
    public ResourceServerDO getServer(String id) {
        return resourceServerValidator.validateExists(id);
    }

    @Override
    public ResourceServerLandingDO getLanding(String id) {
        return resourceServerLandingValidator.validateExists(id);
    }

    @Override
    public ResourceServerBillingDO getBilling(String id) {
        return resourceServerBillingMapper.selectById(id);
    }

    @Override
    public ResourceServerCapacityDO getCapacity(String id) {
        return resourceServerCapacityMapper.selectById(id);
    }

    @Override
    public ResourceServerRuntimeDO getRuntime(String id) {
        return resourceServerRuntimeMapper.selectById(id);
    }

    @Override
    public PageResult<ResourceServerDO> getPage(ServerLandingPageReqVO reqVO) {
        // 子表 (status / ipTypeId) 过滤先拿候选 serverIds
        Set<String> statusFilterIds = null;
        if (StrUtil.isNotBlank(reqVO.getStatus()) || StrUtil.isNotBlank(reqVO.getIpTypeId())) {
            List<ResourceServerLandingDO> rows = resourceServerLandingMapper.selectByFilter(reqVO.getStatus(), reqVO.getIpTypeId());
            statusFilterIds = CollectionUtils.convertSet(rows, ResourceServerLandingDO::getServerId);
            if (CollUtil.isEmpty(statusFilterIds)) {
                return PageResult.empty();
            }
        }
        // 全量拉落地机, 按账单到期升序 (无到期排末) → 快到期的靠前便于续费, 再内存分页 (小规模)
        List<ResourceServerDO> all = resourceServerMapper.selectListByQuery(
                reqVO.getKeyword(), reqVO.getLifecycleState(), reqVO.getRegionCodes(),
                statusFilterIds, ResourceServerTypeEnum.LANDING.getState());
        if (CollUtil.isEmpty(all)) {
            return PageResult.empty();
        }
        Map<String, LocalDate> expiryMap = this.loadExpiryMap(all);
        all.sort(Comparator
                .comparing((ResourceServerDO s) -> expiryMap.get(s.getId()), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ResourceServerDO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int total = all.size();
        int from = Math.min((int) ((reqVO.getPageNo() - 1L) * reqVO.getPageSize()), total);
        int to = Math.min(from + reqVO.getPageSize(), total);
        return PageResult.of((long) total, new ArrayList<>(all.subList(from, to)));
    }

    /** 批量取落地机账单到期日 (serverId → 到期日); 无到期日的不入表. */
    private Map<String, LocalDate> loadExpiryMap(List<ResourceServerDO> servers) {
        Set<String> ids = CollectionUtils.convertSet(servers, ResourceServerDO::getId);
        Map<String, LocalDate> map = new HashMap<>();
        for (ResourceServerBillingDO bill : resourceServerBillingMapper.selectBatchIds(ids)) {
            if (ObjectUtil.isNotNull(bill.getExpiresAt())) {
                map.put(bill.getServerId(), bill.getExpiresAt());
            }
        }
        return map;
    }

    @Override
    public Map<String, Long> getSummary() {
        Map<String, Long> result = new HashMap<>();
        long total = 0;
        long installing = 0, ready = 0, live = 0, retired = 0;
        long available = 0, occupied = 0, reserved = 0;

        List<ResourceServerDO> servers = resourceServerMapper.selectByServerType(ResourceServerTypeEnum.LANDING.getState());
        total = servers.size();
        for (ResourceServerDO s : servers) {
            String state = s.getLifecycleState();
            if (ResourceServerLifecycleEnum.INSTALLING.matches(state)) installing++;
            else if (ResourceServerLifecycleEnum.READY.matches(state)) ready++;
            else if (ResourceServerLifecycleEnum.LIVE.matches(state)) live++;
            else if (ResourceServerLifecycleEnum.RETIRED.matches(state)) retired++;
        }
        if (CollUtil.isNotEmpty(servers)) {
            List<String> ids = CollectionUtils.convertList(servers, ResourceServerDO::getId);
            List<ResourceServerLandingDO> landings = resourceServerLandingMapper.selectBatchIds(ids);
            for (ResourceServerLandingDO l : landings) {
                ResourceServerLandingStatusEnum st = ResourceServerLandingStatusEnum.fromState(l.getStatus());
                if (ObjectUtil.isNull(st)) continue;
                switch (st) {
                    case AVAILABLE -> available++;
                    case OCCUPIED -> occupied++;
                    case RESERVED -> reserved++;
                }
            }
        }
        result.put("total", total);
        result.put("lifecycle_INSTALLING", installing);
        result.put("lifecycle_READY", ready);
        result.put("lifecycle_LIVE", live);
        result.put("lifecycle_RETIRED", retired);
        result.put("status_AVAILABLE", available);
        result.put("status_OCCUPIED", occupied);
        result.put("status_RESERVED", reserved);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceServerDO occupyById(String serverId, String memberUserId) {
        ResourceServerDO srv = resourceServerValidator.validateExists(serverId);
        int rows = resourceServerLandingMapper.markOccupied(serverId, memberUserId, LocalDateTime.now());
        if (rows != 1) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "landing " + serverId + " 占用失败 (并发被抢或状态不是 AVAILABLE)");
        }
        return srv;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseForRevoke(String serverId) {
        // 退订直接转 "可分配"
        int rows = resourceServerLandingMapper.markAvailable(serverId);
        if (rows == 0) {
            log.warn("[landing-release] markAvailable rows=0 serverId={}", serverId);
        }
    }

    @Override
    public Map<String, ResourceServerLandingDO> getLandingMap(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Map.of();
        List<ResourceServerLandingDO> rows = resourceServerLandingMapper.selectBatchIds(serverIds);
        return CollectionUtils.convertMap(rows, ResourceServerLandingDO::getServerId);
    }

    @Override
    public SubtablesBundle batchLoadSubtables(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            Map<String, ResourceServerLandingDO> emptyLanding = Map.of();
            Map<String, ResourceServerBillingDO> emptyBill = Map.of();
            Map<String, ResourceServerCapacityDO> emptyCap = Map.of();
            Map<String, ResourceServerRuntimeDO> emptyRt = Map.of();
            return new SubtablesBundle(emptyLanding, emptyBill, emptyCap, emptyRt);
        }
        Map<String, ResourceServerLandingDO> landings = CollectionUtils.convertMap(
                resourceServerLandingMapper.selectBatchIds(serverIds), ResourceServerLandingDO::getServerId);
        Map<String, ResourceServerBillingDO> bills = CollectionUtils.convertMap(
                resourceServerBillingMapper.selectBatchIds(serverIds), ResourceServerBillingDO::getServerId);
        Map<String, ResourceServerCapacityDO> caps = CollectionUtils.convertMap(
                resourceServerCapacityMapper.selectBatchIds(serverIds), ResourceServerCapacityDO::getServerId);
        Map<String, ResourceServerRuntimeDO> runtimes = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
        return new SubtablesBundle(landings, bills, caps, runtimes);
    }

    @Override
    public List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return List.of();
        }
        Map<String, ResourceServerDO> srvMap = CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);
        Map<String, ResourceServerLandingDO> landingMap = CollectionUtils.convertMap(
                resourceServerLandingMapper.selectBatchIds(serverIds), ResourceServerLandingDO::getServerId);
        return serverIds.stream()
                .map(id -> {
                    ResourceServerDO s = srvMap.get(id);
                    return ObjectUtil.isNull(s) ? null
                            : ResourceServerLandingConvert.INSTANCE.toSummary(s, landingMap.get(id));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
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
        List<ResourceServerLandingDO> landings =
                resourceServerLandingMapper.selectByServerIdsAndIpType(serverMap.keySet(), ipTypeId);
        if (CollUtil.isEmpty(landings)) {
            return List.of();
        }
        // ③ 容量: 批量取容量子表, 逐台按套餐流量 / 带宽阈值过滤后转概要
        Map<String, ResourceServerCapacityDO> capMap = CollectionUtils.convertMap(
                resourceServerCapacityMapper.selectBatchIds(CollectionUtils.convertSet(landings, ResourceServerLandingDO::getServerId)),
                ResourceServerCapacityDO::getServerId);
        Set<String> allocatable = resourceServerAdmission.filterAllocatable(serverMap.keySet());
        List<LandingSummaryDTO> matched = new ArrayList<>();
        for (ResourceServerLandingDO landing : landings) {
            // 健康准入 + 规格达标 才进候选
            if (!allocatable.contains(landing.getServerId())
                    || this.belowPlanSpec(capMap.get(landing.getServerId()), minTrafficGb, minBandwidthMbps)) {
                continue;
            }
            matched.add(ResourceServerLandingConvert.INSTANCE.toSummary(serverMap.get(landing.getServerId()), landing));
        }
        return matched;
    }

    /** 落地机配额 / 带宽是否达不到套餐要求 (机器侧 0 或空表示不限, 视为达标). */
    private boolean belowPlanSpec(ResourceServerCapacityDO cap, int minTrafficGb, int minBandwidthMbps) {
        Integer quotaGb = ObjectUtil.isNull(cap) ? null : cap.getMonthlyTrafficGb();
        Integer bandwidthMbps = ObjectUtil.isNull(cap) ? null : cap.getBandwidthLimitMbps();
        if (ObjectUtil.isNotNull(quotaGb) && quotaGb > 0 && quotaGb < minTrafficGb) {
            return true;
        }
        return ObjectUtil.isNotNull(bandwidthMbps) && bandwidthMbps > 0 && bandwidthMbps < minBandwidthMbps;
    }

    @Override
    public Map<String, PlanCapacityDTO> countCapacityForPlans(Collection<PlanSpecDTO> specs) {
        if (CollUtil.isEmpty(specs)) {
            return Map.of();
        }
        // ① 批量捞 (固定 3 查询, 不随套餐数增长): 涉及区域的运行中落地机 → 这批机器里涉及 IP 类型的落地子表 → 容量子表
        Set<String> regions = CollectionUtils.convertSet(specs, PlanSpecDTO::getRegionCode);
        Set<String> ipTypeIds = CollectionUtils.convertSet(specs, PlanSpecDTO::getIpTypeId);
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectLiveLandingsByRegions(regions), ResourceServerDO::getId);
        List<ResourceServerLandingDO> landings = MapUtil.isEmpty(serverMap) ? List.of()
                : resourceServerLandingMapper.selectByServerIdsAndIpTypes(serverMap.keySet(), ipTypeIds);
        Map<String, ResourceServerCapacityDO> capMap = CollUtil.isEmpty(landings) ? Map.of()
                : CollectionUtils.convertMap(resourceServerCapacityMapper.selectBatchIds(
                        CollectionUtils.convertSet(landings, ResourceServerLandingDO::getServerId)),
                        ResourceServerCapacityDO::getServerId);
        Set<String> allocatable = resourceServerAdmission.filterAllocatable(serverMap.keySet());
        // ② 落地机按 (区域 + IP 类型) 分桶, 让每个套餐 O(1) 命中自己的候选
        Map<String, List<ResourceServerLandingDO>> bucket = new HashMap<>();
        for (ResourceServerLandingDO landing : landings) {
            ResourceServerDO server = serverMap.get(landing.getServerId());
            bucket.computeIfAbsent(this.regionIpKey(server.getRegion(), landing.getIpTypeId()),
                    k -> new ArrayList<>()).add(landing);
        }
        // ③ 每个套餐在自己的桶里按容量阈值过滤 + status 计数 (纯内存, 不再查库)
        Map<String, PlanCapacityDTO> result = new HashMap<>(specs.size());
        for (PlanSpecDTO spec : specs) {
            List<ResourceServerLandingDO> candidates = bucket.getOrDefault(
                    this.regionIpKey(spec.getRegionCode(), spec.getIpTypeId()), List.of());
            int total = 0;
            int available = 0;
            int occupied = 0;
            for (ResourceServerLandingDO landing : candidates) {
                // 占用中的落地机在服务本套餐订阅, 即便被降配到不达标也照常计入 (运行时按 min(套餐,落地机) 带宽限速)
                if (ResourceServerLandingStatusEnum.OCCUPIED.matches(landing.getStatus())) {
                    total++;
                    occupied++;
                    continue;
                }
                // 空闲机器需健康可分配 + 达标 才算"可售"
                if (!allocatable.contains(landing.getServerId())
                        || this.belowPlanSpec(capMap.get(landing.getServerId()), spec.getTrafficGb(), spec.getBandwidthMbps())) {
                    continue;
                }
                total++;
                if (ResourceServerLandingStatusEnum.AVAILABLE.matches(landing.getStatus())) {
                    available++;
                }
            }
            result.put(spec.getPlanId(), new PlanCapacityDTO(total, available, occupied));
        }
        return result;
    }

    /** (区域 + IP 类型) 复合 key; 把落地机按套餐匹配维度分桶. */
    private String regionIpKey(String region, String ipTypeId) {
        return region + '|' + ipTypeId;
    }

}
