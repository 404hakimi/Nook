package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.controller.resource.vo.ResourceServerCapacityUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.service.resource.ResourceServerCapacityService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务器容量 Service 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerCapacityServiceImpl implements ResourceServerCapacityService {

    @Resource
    private ResourceServerCapacityMapper resourceServerCapacityMapper;

    @Override
    public ResourceServerCapacityDO get(String serverId) {
        return resourceServerCapacityMapper.selectById(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuota(String serverId, ResourceServerCapacityUpdateReqVO reqVO) {
        resourceServerCapacityMapper.updateQuota(serverId, reqVO.getMonthlyTrafficGb(), reqVO.getBandwidthLimitMbps(),
                reqVO.getQuotaResetPolicy(), reqVO.getResetDay());
    }
}
