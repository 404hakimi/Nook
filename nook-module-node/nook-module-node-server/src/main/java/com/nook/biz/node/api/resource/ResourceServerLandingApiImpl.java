package com.nook.biz.node.api.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import jakarta.annotation.Resource;
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
public class ResourceServerLandingApiImpl implements ResourceServerLandingApi {

    @Resource
    private ResourceServerLandingService resourceServerLandingService;

    @Override
    public List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds) {
        return resourceServerLandingService.listSummaryByServerIds(serverIds);
    }

    @Override
    public List<LandingSummaryDTO> findMatchingForPlan(String region, String ipTypeId,
                                                       int minTrafficGb, int minBandwidthMbps) {
        return resourceServerLandingService.findMatchingForPlan(region, ipTypeId, minTrafficGb, minBandwidthMbps);
    }

    @Override
    public Map<String, PlanCapacityDTO> countCapacityForPlans(Collection<PlanSpecDTO> specs) {
        return resourceServerLandingService.countCapacityForPlans(specs);
    }

    @Override
    public int getSocks5Port(String serverId) {
        ResourceServerLandingDO landing = resourceServerLandingService.getLanding(serverId);
        Integer port = ObjectUtil.isNull(landing) ? null : landing.getSocks5Port();
        return ObjectUtil.isNull(port) ? 0 : port;
    }

    @Override
    public void occupyLanding(String landingServerId, String memberUserId) {
        resourceServerLandingService.occupyById(landingServerId, memberUserId);
    }

    @Override
    public void releaseLanding(String landingServerId) {
        resourceServerLandingService.releaseForRevoke(landingServerId);
    }
}
