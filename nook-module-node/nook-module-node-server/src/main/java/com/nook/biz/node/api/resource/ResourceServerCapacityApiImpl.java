package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器容量 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerCapacityApiImpl implements ResourceServerCapacityApi {

    private final ResourceServerCapacityMapper resourceServerCapacityMapper;

    @Override
    public void addUsedTrafficBytes(String serverId, long bytes) {
        resourceServerCapacityMapper.updateUsedTrafficBytes(serverId, bytes);
    }

    @Override
    public Map<String, ResourceServerCapacityRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerCapacityMapper.selectBatchIds(serverIds),
                ResourceServerCapacityDO::getServerId,
                row -> BeanUtils.toBean(row, ResourceServerCapacityRespDTO.class));
    }
}
