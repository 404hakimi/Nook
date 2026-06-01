package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 服务器 SSH 凭据 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerCredentialApiImpl implements ResourceServerCredentialApi {

    private final ResourceServerCredentialMapper resourceServerCredentialMapper;
    private final ResourceServerCredentialService credentialService;

    @Override
    public ResourceServerCredentialRespDTO getByServerId(String serverId) {
        ResourceServerCredentialDO row = credentialService.getServerCredential(serverId);
        return row == null ? null : BeanUtils.toBean(row, ResourceServerCredentialRespDTO.class);
    }

    @Override
    public ResourceServerCredentialRespDTO requireByServerId(String serverId) {
        return BeanUtils.toBean(credentialService.requireByServerId(serverId), ResourceServerCredentialRespDTO.class);
    }

    @Override
    public Map<String, ResourceServerCredentialRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerCredentialMapper.selectBatchIds(serverIds),
                ResourceServerCredentialDO::getServerId,
                row -> BeanUtils.toBean(row, ResourceServerCredentialRespDTO.class));
    }
}
