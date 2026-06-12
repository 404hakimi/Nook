package com.nook.biz.node.api.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.node.convert.resource.ResourceServerLandingConvert;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.service.resource.ResourceServerAdmission;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 落地机 核心业务 API实现
 *
 * @author nook
 */
@Service
public class ResourceServerLandingApiImpl implements ResourceServerLandingApi {

    @Resource
    private ResourceServerLandingService resourceServerLandingService;
    @Resource
    private ResourceServerAdmission resourceServerAdmission;

    @Override
    public List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds) {
        // 跨模块边界: 调 Service 拿 DO, 由 Convert 拼概要 DTO
        Map<String, ResourceServerDO> serverMap = resourceServerLandingService.getServerMap(serverIds);
        Map<String, Socks5InstallDO> landingMap = resourceServerLandingService.getLandingMap(serverIds);
        return ResourceServerLandingConvert.INSTANCE.toSummaries(serverIds, serverMap, landingMap);
    }

    @Override
    public List<LandingSummaryDTO> findMatchingForPlan(String region, String ipTypeId,
                                                       int minTrafficGb, int minBandwidthMbps) {
        // 选址核心在 ResourceServerAdmission, 本类只做跨模块转发
        return resourceServerAdmission.findMatchingForPlan(region, ipTypeId, minTrafficGb, minBandwidthMbps);
    }

    @Override
    public Map<String, PlanCapacityDTO> countCapacityForPlans(Collection<PlanSpecDTO> specs) {
        return resourceServerAdmission.countCapacityForPlans(specs);
    }

    @Override
    public int getSocks5Port(String serverId) {
        Socks5InstallDO landing = resourceServerLandingService.getLanding(serverId);
        Integer port = ObjectUtil.isNull(landing) ? null : landing.getSocks5Port();
        return ObjectUtil.isNull(port) ? 0 : port;
    }
}
