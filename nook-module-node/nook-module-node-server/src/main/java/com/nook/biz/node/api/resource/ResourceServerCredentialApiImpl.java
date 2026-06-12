package com.nook.biz.node.api.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.convert.resource.ResourceServerCredentialConvert;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import com.nook.biz.node.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 服务器 SSH 凭据 Api 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerCredentialApiImpl implements ResourceServerCredentialApi {

    @Resource
    private ResourceServerCredentialMapper resourceServerCredentialMapper;
    @Resource
    private ResourceServerCredentialService resourceServerCredentialService;

    @Override
    public ResourceServerCredentialRespDTO getByServerId(String serverId) {
        ResourceServerCredentialDO row = resourceServerCredentialService.getServerCredential(serverId);
        return ObjectUtil.isNull(row) ? null : ResourceServerCredentialConvert.INSTANCE.toRespDTO(row);
    }

    @Override
    public ResourceServerCredentialRespDTO requireByServerId(String serverId) {
        return ResourceServerCredentialConvert.INSTANCE.toRespDTO(
                resourceServerCredentialService.requireByServerId(serverId));
    }

    @Override
    public Map<String, ResourceServerCredentialRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                resourceServerCredentialMapper.selectBatchIds(serverIds),
                ResourceServerCredentialDO::getServerId,
                ResourceServerCredentialConvert.INSTANCE::toRespDTO);
    }
}
