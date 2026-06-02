package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link ResourceServerLandingApi} 实现; 转发到 {@link ResourceServerLandingService}.
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerLandingApiImpl implements ResourceServerLandingApi {

    private final ResourceServerLandingService landingService;

    @Override
    public List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds) {
        return landingService.listSummaryByServerIds(serverIds);
    }

    @Override
    public List<LandingSummaryDTO> findMatchingForPlan(String region, String ipTypeId,
                                                       int minTrafficGb, int minBandwidthMbps) {
        return landingService.findMatchingForPlan(region, ipTypeId, minTrafficGb, minBandwidthMbps);
    }

    @Override
    public Map<String, PlanCapacityDTO> countCapacityForPlans(Collection<PlanSpecDTO> specs) {
        return landingService.countCapacityForPlans(specs);
    }

    @Override
    public int getSocks5Port(String serverId) {
        ResourceServerLandingDO landing = landingService.getLanding(serverId);
        Integer port = landing == null ? null : landing.getSocks5Port();
        return port == null ? 0 : port;
    }
}
