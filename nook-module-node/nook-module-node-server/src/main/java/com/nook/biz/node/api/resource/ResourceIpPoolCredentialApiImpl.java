package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceIpPoolCredentialRespDTO;
import com.nook.biz.node.service.resource.ResourceIpPoolCredentialService;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * IP 池 SSH 凭据 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceIpPoolCredentialApiImpl implements ResourceIpPoolCredentialApi {

    private final ResourceIpPoolCredentialService credentialService;

    @Override
    public ResourceIpPoolCredentialRespDTO getByIpId(String ipId) {
        return BeanUtils.toBean(credentialService.get(ipId), ResourceIpPoolCredentialRespDTO.class);
    }

    @Override
    public ResourceIpPoolCredentialRespDTO requireByIpId(String ipId) {
        return BeanUtils.toBean(credentialService.requireByIpId(ipId), ResourceIpPoolCredentialRespDTO.class);
    }
}
