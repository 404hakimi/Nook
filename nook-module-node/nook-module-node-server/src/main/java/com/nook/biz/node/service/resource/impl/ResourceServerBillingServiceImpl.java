package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.resource.vo.ResourceServerBillingUpdateReqVO;
import com.nook.biz.node.entity.ResourceServerBillingDO;
import com.nook.biz.node.mapper.ResourceServerBillingMapper;
import com.nook.biz.node.service.resource.ResourceServerBillingService;
import com.nook.common.utils.object.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 服务器账面 Service 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerBillingServiceImpl implements ResourceServerBillingService {

    @Resource
    private ResourceServerBillingMapper resourceServerBillingMapper;

    @Override
    public ResourceServerBillingDO get(String serverId) {
        return resourceServerBillingMapper.selectById(serverId);
    }

    @Override
    public void create(String serverId, ResourceServerBillingUpdateReqVO reqVO) {
        ResourceServerBillingDO entity = ObjectUtil.isNull(reqVO)
                ? new ResourceServerBillingDO()
                : BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
        entity.setServerId(serverId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resourceServerBillingMapper.insert(entity);
    }

    @Override
    public void update(String serverId, ResourceServerBillingUpdateReqVO reqVO) {
        ResourceServerBillingDO patch = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
        patch.setServerId(serverId);
        resourceServerBillingMapper.updateBySelective(patch);
    }
}
