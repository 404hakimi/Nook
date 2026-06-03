package com.nook.biz.node.api.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
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
        return ObjectUtil.isNull(row) ? null : BeanUtils.toBean(row, ResourceServerCredentialRespDTO.class);
    }

    @Override
    public ResourceServerCredentialRespDTO requireByServerId(String serverId) {
        return BeanUtils.toBean(resourceServerCredentialService.requireByServerId(serverId),
                ResourceServerCredentialRespDTO.class);
    }

    @Override
    public Map<String, ResourceServerCredentialRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                resourceServerCredentialMapper.selectBatchIds(serverIds),
                ResourceServerCredentialDO::getServerId,
                row -> BeanUtils.toBean(row, ResourceServerCredentialRespDTO.class));
    }
}
