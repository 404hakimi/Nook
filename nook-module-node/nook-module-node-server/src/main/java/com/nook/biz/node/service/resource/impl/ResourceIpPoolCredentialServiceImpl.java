package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCredentialUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCredentialDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolCredentialMapper;
import com.nook.biz.node.service.resource.ResourceIpPoolCredentialService;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IP 池 SSH 凭据 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceIpPoolCredentialServiceImpl implements ResourceIpPoolCredentialService {

    private final ResourceIpPoolCredentialMapper credentialMapper;
    private final ResourceIpPoolValidator ipPoolValidator;

    @Override
    public ResourceIpPoolCredentialDO get(String ipId) {
        return credentialMapper.selectById(ipId);
    }

    @Override
    public ResourceIpPoolCredentialDO requireByIpId(String ipId) {
        ResourceIpPoolCredentialDO row = credentialMapper.selectById(ipId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_SSH_CRED_MISSING, ipId);
        }
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String ipId, ResourceIpPoolCredentialUpdateReqVO reqVO) {
        ipPoolValidator.validateExists(ipId);
        ResourceIpPoolCredentialDO patch = BeanUtils.toBean(reqVO, ResourceIpPoolCredentialDO.class);
        patch.setIpId(ipId);
        // 密码留空 = 保留原值 (前端 update form 不显示密码, 改密码才填)
        if (StrUtil.isBlank(patch.getSshPassword())) {
            patch.setSshPassword(null);
        }
        credentialMapper.updateBySelective(patch);
    }
}
