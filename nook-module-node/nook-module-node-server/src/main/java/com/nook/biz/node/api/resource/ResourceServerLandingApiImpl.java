package com.nook.biz.node.api.resource;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ResourceServerLandingApi} 实现 (组合主表 lifecycle + landing 子表 status/ipType).
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerLandingApiImpl implements ResourceServerLandingApi {

    private static final String LIVE = "LIVE";
    private static final String LANDING = ResourceServerTypeEnum.LANDING.getState();

    private final ResourceServerMapper serverMapper;
    private final ResourceServerLandingMapper landingMapper;
    private final ResourceServerCapacityMapper capacityMapper;

    @Override
    public List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, ResourceServerDO> srvMap = serverMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(ResourceServerDO::getId, Function.identity()));
        Map<String, ResourceServerLandingDO> landingMap = landingMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(ResourceServerLandingDO::getServerId, Function.identity()));
        return serverIds.stream()
                .map(id -> {
                    ResourceServerDO s = srvMap.get(id);
                    if (s == null) {
                        return null;
                    }
                    LandingSummaryDTO dto = new LandingSummaryDTO();
                    dto.setServerId(id);
                    dto.setLifecycleState(s.getLifecycleState());
                    dto.setIpAddress(s.getIpAddress());
                    ResourceServerLandingDO l = landingMap.get(id);
                    if (l != null) {
                        dto.setStatus(l.getStatus());
                        dto.setIpTypeId(l.getIpTypeId());
                    }
                    return dto;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<LandingSummaryDTO> findMatchingForPlan(String region, String ipTypeId,
                                                       int minTrafficGb, int minBandwidthMbps) {
        if (StrUtil.isBlank(region) || StrUtil.isBlank(ipTypeId)) {
            return Collections.emptyList();
        }
        // 1. 该 IP 类型的落地机子表
        List<ResourceServerLandingDO> landings = landingMapper.selectList(
                Wrappers.<ResourceServerLandingDO>lambdaQuery()
                        .eq(ResourceServerLandingDO::getIpTypeId, ipTypeId));
        if (landings.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> ids = landings.stream()
                .map(ResourceServerLandingDO::getServerId).collect(Collectors.toSet());
        // 2. 主表过滤: 同区域 + landing 角色 + LIVE (selectBatchIds 自动滤 deleted)
        Map<String, ResourceServerDO> srvMap = serverMapper.selectBatchIds(ids).stream()
                .filter(s -> LANDING.equals(s.getServerType())
                        && LIVE.equals(s.getLifecycleState())
                        && region.equals(s.getRegion()))
                .collect(Collectors.toMap(ResourceServerDO::getId, Function.identity()));
        if (srvMap.isEmpty()) {
            return Collections.emptyList();
        }
        // 3. 容量过滤: monthly_traffic_gb / bandwidth_limit_mbps ≥ 套餐 (0/null=不限)
        Map<String, ResourceServerCapacityDO> capMap = capacityMapper.selectBatchIds(srvMap.keySet()).stream()
                .collect(Collectors.toMap(ResourceServerCapacityDO::getServerId, Function.identity()));
        List<LandingSummaryDTO> out = new ArrayList<>();
        for (ResourceServerLandingDO l : landings) {
            ResourceServerDO s = srvMap.get(l.getServerId());
            if (s == null) {
                continue;
            }
            ResourceServerCapacityDO cap = capMap.get(l.getServerId());
            Integer q = cap == null ? null : cap.getMonthlyTrafficGb();
            Integer bw = cap == null ? null : cap.getBandwidthLimitMbps();
            if (q != null && q > 0 && q < minTrafficGb) {
                continue;
            }
            if (bw != null && bw > 0 && bw < minBandwidthMbps) {
                continue;
            }
            LandingSummaryDTO dto = new LandingSummaryDTO();
            dto.setServerId(l.getServerId());
            dto.setLifecycleState(s.getLifecycleState());
            dto.setStatus(l.getStatus());
            dto.setIpTypeId(l.getIpTypeId());
            dto.setIpAddress(s.getIpAddress());
            out.add(dto);
        }
        return out;
    }

    @Override
    public PlanCapacityDTO countCapacityForPlan(String region, String ipTypeId,
                                                int minTrafficGb, int minBandwidthMbps) {
        List<LandingSummaryDTO> matching = findMatchingForPlan(region, ipTypeId, minTrafficGb, minBandwidthMbps);
        int total = matching.size();
        int avail = (int) matching.stream()
                .filter(l -> ResourceServerLandingStatusEnum.AVAILABLE.matches(l.getStatus())).count();
        int occ = (int) matching.stream()
                .filter(l -> ResourceServerLandingStatusEnum.OCCUPIED.matches(l.getStatus())).count();
        return new PlanCapacityDTO(total, avail, occ);
    }
}
