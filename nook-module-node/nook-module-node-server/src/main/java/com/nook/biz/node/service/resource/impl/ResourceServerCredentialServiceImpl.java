package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.event.ServerCredentialChangedEvent;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.validator.ResourceServerCredentialValidator;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ResourceServerCredentialServiceImpl implements ResourceServerCredentialService {

    private final ResourceServerCredentialMapper credentialMapper;
    private final ResourceServerCredentialValidator credentialValidator;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public ResourceServerCredentialDO getServerCredential(String serverId) {
        return credentialMapper.selectById(serverId);
    }

    @Override
    public ResourceServerCredentialDO requireByServerId(String serverId) {
        return credentialValidator.validateExists(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(String serverId, ResourceServerCredentialUpdateReqVO reqVO) {
        ResourceServerCredentialDO entity = BeanUtils.toBean(reqVO, ResourceServerCredentialDO.class);
        entity.setServerId(serverId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        credentialMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String serverId, ResourceServerCredentialUpdateReqVO reqVO) {
        ResourceServerCredentialDO current = credentialValidator.validateExists(serverId);
        credentialValidator.validateUpdate(serverId, current, reqVO);
        ResourceServerCredentialDO patch = BeanUtils.toBean(reqVO, ResourceServerCredentialDO.class);
        patch.setServerId(serverId);
        // 密码留空 = 保留原值 (前端 update form 不显示密码, 改密码才填)
        if (StrUtil.isBlank(patch.getSshPassword())) {
            patch.setSshPassword(null);
        }
        credentialMapper.updateBySelective(patch);
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(serverId));
    }
}
