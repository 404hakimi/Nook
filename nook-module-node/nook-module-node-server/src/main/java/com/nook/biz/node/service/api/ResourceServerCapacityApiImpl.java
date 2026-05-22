package com.nook.biz.node.service.api;

import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** node-api {@link ResourceServerCapacityApi} 实现. */
@Service
@RequiredArgsConstructor
public class ResourceServerCapacityApiImpl implements ResourceServerCapacityApi {

    private final ResourceServerCapacityMapper resourceServerCapacityMapper;

    @Override
    public void addUsedTrafficBytes(String serverId, long bytes) {
        resourceServerCapacityMapper.updateUsedTrafficBytes(serverId, bytes);
    }
}
