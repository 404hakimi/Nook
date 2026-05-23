package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.service.resource.ResourceServerCapacityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 服务器容量 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerCapacityServiceImpl implements ResourceServerCapacityService {

    private final ResourceServerCapacityMapper capacityMapper;

    @Override
    public ResourceServerCapacityDO get(String serverId) {
        return capacityMapper.selectById(serverId);
    }
}
