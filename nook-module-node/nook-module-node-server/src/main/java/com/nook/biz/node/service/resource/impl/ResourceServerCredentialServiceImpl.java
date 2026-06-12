package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import com.nook.biz.node.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.validator.ResourceServerCredentialValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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

    @Override
    public ResourceServerCredentialDO getServerCredential(String serverId) {
        return resourceServerCredentialMapper.selectById(serverId);
    }

    @Override
    public ResourceServerCredentialDO requireByServerId(String serverId) {
        return resourceServerCredentialValidator.validateExists(serverId);
    }

    @Override
    public void create(String serverId, ResourceServerCredentialUpdateReqVO reqVO) {
        ResourceServerCredentialDO entity = BeanUtils.toBean(reqVO, ResourceServerCredentialDO.class);
        entity.setServerId(serverId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resourceServerCredentialMapper.insert(entity);
    }

    @Override
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
        // 凭据变更后清掉缓存的 SSH 会话, 下次 acquire 用最新凭据重建
        SshSessions.invalidate(serverId);
    }
}
