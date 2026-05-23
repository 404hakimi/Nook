package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolBillingUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolBillingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolBillingMapper;
import com.nook.biz.node.service.resource.ResourceIpPoolBillingService;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IP 池账面 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceIpPoolBillingServiceImpl implements ResourceIpPoolBillingService {

    private final ResourceIpPoolBillingMapper billingMapper;
    private final ResourceIpPoolValidator ipPoolValidator;

    @Override
    public ResourceIpPoolBillingDO get(String ipId) {
        return billingMapper.selectById(ipId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String ipId, ResourceIpPoolBillingUpdateReqVO reqVO) {
        ipPoolValidator.validateExists(ipId);
        ResourceIpPoolBillingDO patch = BeanUtils.toBean(reqVO, ResourceIpPoolBillingDO.class);
        patch.setIpId(ipId);
        billingMapper.updateBySelective(patch);
    }
}
