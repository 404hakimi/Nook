package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerQuotaRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerQuotaDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerQuotaMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerTrafficMapper;
import com.nook.biz.node.service.resource.ResourceServerTrafficService;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源服务器额度 Api 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerQuotaApiImpl implements ResourceServerQuotaApi {

    @Resource
    private ResourceServerQuotaMapper resourceServerQuotaMapper;
    @Resource
    private ResourceServerTrafficMapper resourceServerTrafficMapper;
    @Resource
    private ResourceServerTrafficService resourceServerTrafficService;

    @Override
    public void applyNicTraffic(String serverId, long rxBytes, long txBytes, Long bizUpBytes, Long bizDownBytes) {
        resourceServerTrafficService.applyNicTraffic(serverId, rxBytes, txBytes, bizUpBytes, bizDownBytes);
    }

    @Override
    public Map<String, ResourceServerQuotaRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) {
            return Map.of();
        }
        // 配额配置(每机 1 行)+ 当周期测量行(可能未上报)现拼成 DTO
        Map<String, ResourceServerTrafficDO> trafficMap = CollectionUtils.convertMap(
                resourceServerTrafficMapper.selectCurrentByServerIds(serverIds), ResourceServerTrafficDO::getServerId);
        Map<String, ResourceServerQuotaRespDTO> result = new HashMap<>();
        for (ResourceServerQuotaDO quota : resourceServerQuotaMapper.selectBatchIds(serverIds)) {
            result.put(quota.getServerId(), this.toDTO(quota, trafficMap.get(quota.getServerId())));
        }
        return result;
    }

    /** 配额配置 + 当周期测量(可空)拼成对外 DTO. */
    private ResourceServerQuotaRespDTO toDTO(ResourceServerQuotaDO quota, ResourceServerTrafficDO traffic) {
        ResourceServerQuotaRespDTO dto = new ResourceServerQuotaRespDTO();
        dto.setServerId(quota.getServerId());
        dto.setTotalGb(quota.getTotalGb());
        dto.setBandwidthMbps(quota.getBandwidthMbps());
        if (traffic != null) {
            dto.setRxBytes(traffic.getRxBytes());
            dto.setTxBytes(traffic.getTxBytes());
            dto.setUsedBytes(traffic.getUsedBytes());
            dto.setCounterUpBytes(traffic.getCounterUpBytes());
            dto.setCounterDownBytes(traffic.getCounterDownBytes());
            dto.setThrottleState(traffic.getThrottleState());
        }
        return dto;
    }
}
