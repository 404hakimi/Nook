package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCapacityUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCapacityDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolCapacityMapper;
import com.nook.biz.node.service.resource.ResourceIpPoolCapacityService;
import com.nook.biz.node.validator.ResourceIpPoolBandwidthValidator;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IP 池容量监控 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceIpPoolCapacityServiceImpl implements ResourceIpPoolCapacityService {

    private final ResourceIpPoolCapacityMapper capacityMapper;
    private final ResourceIpPoolValidator ipPoolValidator;
    private final ResourceIpPoolBandwidthValidator bandwidthValidator;

    @Override
    public ResourceIpPoolCapacityDO get(String ipId) {
        return capacityMapper.selectById(ipId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String ipId, ResourceIpPoolCapacityUpdateReqVO reqVO) {
        ipPoolValidator.validateExists(ipId);
        // 链路校验: 改限速时校验 SKU 池内 Σ落地机带宽 ≤ 线路机带宽 × 0.9 (当前 stub)
        bandwidthValidator.validateLinkCapacity(ipId, reqVO.getBandwidthLimitMbps());

        ResourceIpPoolCapacityDO patch = BeanUtils.toBean(reqVO, ResourceIpPoolCapacityDO.class);
        patch.setIpId(ipId);
        capacityMapper.updateBySelective(patch);
    }
}
