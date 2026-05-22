package com.nook.biz.node.api.resource;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** node-api {@link ResourceServerApi} 实现: 薄壳 facade 委托 validator + mapper. */
@Service
@RequiredArgsConstructor
public class ResourceServerApiImpl implements ResourceServerApi {

    private final ResourceServerValidator serverValidator;
    private final ResourceServerMapper resourceServerMapper;

    @Override
    public ResourceServerRespDTO validateExists(String serverId) {
        return BeanUtils.toBean(serverValidator.validateExists(serverId), ResourceServerRespDTO.class);
    }

    @Override
    public ResourceServerRespDTO getByAgentToken(String agentToken) {
        ResourceServerDO srv = resourceServerMapper.selectByAgentToken(agentToken);
        return srv == null ? null : BeanUtils.toBean(srv, ResourceServerRespDTO.class);
    }

    @Override
    public List<ResourceServerRespDTO> listAll() {
        List<ResourceServerDO> servers = resourceServerMapper.selectList(
                Wrappers.<ResourceServerDO>lambdaQuery().eq(ResourceServerDO::getDeleted, 0));
        return BeanUtils.toBean(servers, ResourceServerRespDTO.class);
    }
}
