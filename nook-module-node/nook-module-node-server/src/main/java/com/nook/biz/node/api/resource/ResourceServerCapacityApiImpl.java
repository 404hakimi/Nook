package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.service.resource.ResourceServerTrafficService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器容量 Api 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerCapacityApiImpl implements ResourceServerCapacityApi {

    @Resource
    private ResourceServerCapacityMapper resourceServerCapacityMapper;
    @Resource
    private ResourceServerTrafficService resourceServerTrafficService;

    @Override
    public void applyNicTraffic(String serverId, long rxBytes, long txBytes, Long bizUsedBytes) {
        resourceServerTrafficService.applyNicTraffic(serverId, rxBytes, txBytes, bizUsedBytes);
    }

    @Override
    public Map<String, ResourceServerCapacityRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                resourceServerCapacityMapper.selectBatchIds(serverIds),
                ResourceServerCapacityDO::getServerId,
                row -> BeanUtils.toBean(row, ResourceServerCapacityRespDTO.class));
    }
}
