package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.controller.resource.vo.ResourceServerCapacityUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.service.resource.ResourceServerCapacityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuota(String serverId, ResourceServerCapacityUpdateReqVO reqVO) {
        capacityMapper.updateQuota(serverId, reqVO.getMonthlyTrafficGb(), reqVO.getBandwidthLimitMbps());
    }
}
