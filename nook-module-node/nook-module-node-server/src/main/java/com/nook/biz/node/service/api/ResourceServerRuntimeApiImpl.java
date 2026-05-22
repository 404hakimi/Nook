package com.nook.biz.node.service.api;

import com.nook.biz.node.api.resource.ResourceServerRuntimeApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/** node-api {@link ResourceServerRuntimeApi} 实现. */
@Service
@RequiredArgsConstructor
public class ResourceServerRuntimeApiImpl implements ResourceServerRuntimeApi {

    private final ResourceServerRuntimeMapper resourceServerRuntimeMapper;

    @Override
    public ResourceServerRuntimeRespDTO getByServerId(String serverId) {
        ResourceServerRuntimeDO row = resourceServerRuntimeMapper.selectById(serverId);
        return row == null ? null : BeanUtils.toBean(row, ResourceServerRuntimeRespDTO.class);
    }

    @Override
    public Map<String, ResourceServerRuntimeRespDTO> listByServerIds(Collection<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(serverIds),
                ResourceServerRuntimeDO::getServerId,
                row -> BeanUtils.toBean(row, ResourceServerRuntimeRespDTO.class));
    }

    @Override
    public int onHeartbeat(String serverId, LocalDateTime at, String agentVersion, String clientIp) {
        return resourceServerRuntimeMapper.onHeartbeat(serverId, at, agentVersion, clientIp);
    }
}
