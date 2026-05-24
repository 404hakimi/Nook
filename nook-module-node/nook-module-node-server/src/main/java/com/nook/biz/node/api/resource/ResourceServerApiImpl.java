package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerPageReqDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 资源服务器 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerApiImpl implements ResourceServerApi {

    private final ResourceServerValidator serverValidator;
    private final ResourceServerService resourceServerService;
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
        return BeanUtils.toBean(resourceServerMapper.selectList(null), ResourceServerRespDTO.class);
    }

    @Override
    public PageResult<ResourceServerRespDTO> page(ResourceServerPageReqDTO req) {
        PageResult<ResourceServerDO> page = resourceServerService.getServerPage(
                BeanUtils.toBean(req, ResourceServerPageReqVO.class));
        return PageResult.of(page.getTotal(),
                BeanUtils.toBean(page.getRecords(), ResourceServerRespDTO.class));
    }

    @Override
    public Map<String, String> getServerNameMap(Collection<String> serverIds) {
        return resourceServerService.getServerNameMap(serverIds);
    }
}
