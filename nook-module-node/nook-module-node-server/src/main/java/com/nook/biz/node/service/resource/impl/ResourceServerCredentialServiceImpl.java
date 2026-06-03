package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.event.ServerCredentialChangedEvent;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.validator.ResourceServerCredentialValidator;
import com.nook.common.utils.object.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 服务器 SSH 凭据 Service 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerCredentialServiceImpl implements ResourceServerCredentialService {

    @Resource
    private ResourceServerCredentialMapper resourceServerCredentialMapper;
    @Resource
    private ResourceServerCredentialValidator resourceServerCredentialValidator;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public ResourceServerCredentialDO getServerCredential(String serverId) {
        return resourceServerCredentialMapper.selectById(serverId);
    }

    @Override
    public ResourceServerCredentialDO requireByServerId(String serverId) {
        return resourceServerCredentialValidator.validateExists(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(String serverId, ResourceServerCredentialUpdateReqVO reqVO) {
        ResourceServerCredentialDO entity = BeanUtils.toBean(reqVO, ResourceServerCredentialDO.class);
        entity.setServerId(serverId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resourceServerCredentialMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String serverId, ResourceServerCredentialUpdateReqVO reqVO) {
        ResourceServerCredentialDO current = resourceServerCredentialValidator.validateExists(serverId);
        resourceServerCredentialValidator.validateUpdate(serverId, current, reqVO);
        ResourceServerCredentialDO patch = BeanUtils.toBean(reqVO, ResourceServerCredentialDO.class);
        patch.setServerId(serverId);
        // 密码留空 = 保留原值 (前端 update form 不显示密码, 改密码才填)
        if (StrUtil.isBlank(patch.getSshPassword())) {
            patch.setSshPassword(null);
        }
        resourceServerCredentialMapper.updateBySelective(patch);
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(serverId));
    }
}
