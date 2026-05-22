package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
