package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceIpPoolRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolMapper;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 资源 IP 池 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceIpPoolApiImpl implements ResourceIpPoolApi {

    private final ResourceIpPoolValidator ipPoolValidator;
    private final ResourceIpPoolMapper resourceIpPoolMapper;

    @Override
    public ResourceIpPoolRespDTO validateExists(String ipId) {
        return BeanUtils.toBean(ipPoolValidator.validateExists(ipId), ResourceIpPoolRespDTO.class);
    }

    @Override
    public ResourceIpPoolRespDTO getByAgentToken(String agentToken) {
        ResourceIpPoolDO row = resourceIpPoolMapper.selectByAgentToken(agentToken);
        return row == null ? null : BeanUtils.toBean(row, ResourceIpPoolRespDTO.class);
    }
}
