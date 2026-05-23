package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.controller.resource.vo.ResourceServerBillingUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerBillingMapper;
import com.nook.biz.node.service.resource.ResourceServerBillingService;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 服务器账面 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerBillingServiceImpl implements ResourceServerBillingService {

    private final ResourceServerBillingMapper billingMapper;

    @Override
    public ResourceServerBillingDO get(String serverId) {
        return billingMapper.selectById(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(String serverId, ResourceServerBillingUpdateReqVO reqVO) {
        ResourceServerBillingDO entity = reqVO == null
                ? new ResourceServerBillingDO()
                : BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
        entity.setServerId(serverId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        billingMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String serverId, ResourceServerBillingUpdateReqVO reqVO) {
        ResourceServerBillingDO patch = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
        patch.setServerId(serverId);
        billingMapper.updateBySelective(patch);
    }
}
