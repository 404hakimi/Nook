package com.nook.biz.node.api.resource;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.convert.resource.ResourceServerConvert;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.service.resource.ResourceServerAdmission;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源服务器 Api 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerApiImpl implements ResourceServerApi {

    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerAdmission resourceServerAdmission;

    @Override
    public ResourceServerRespDTO validateExists(String serverId) {
        return ResourceServerConvert.INSTANCE.toRespDTO(resourceServerValidator.validateExists(serverId));
    }

    @Override
    public ResourceServerRespDTO getServer(String serverId) {
        ResourceServerDO srv = resourceServerMapper.selectById(serverId);
        return ObjectUtil.isNull(srv) ? null : ResourceServerConvert.INSTANCE.toRespDTO(srv);
    }

    @Override
    public ResourceServerRespDTO getByAgentToken(String agentToken) {
        ResourceServerDO srv = resourceServerMapper.selectByAgentToken(agentToken);
        return ObjectUtil.isNull(srv) ? null : ResourceServerConvert.INSTANCE.toRespDTO(srv);
    }

    @Override
    public Map<String, String> getServerNameMap(Collection<String> serverIds) {
        return resourceServerService.getServerNameMap(serverIds);
    }

    @Override
    public List<ResourceServerRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return List.of();
        }
        return ResourceServerConvert.INSTANCE.toRespDTOList(resourceServerMapper.selectBatchIds(serverIds));
    }

    @Override
    public List<ResourceServerRespDTO> findLiveFrontlinesByRegion(String region) {
        if (StrUtil.isBlank(region)) {
            return List.of();
        }
        return ResourceServerConvert.INSTANCE.toRespDTOList(
                resourceServerMapper.selectLiveFrontlinesByRegion(region));
    }

    @Override
    public Set<String> filterAllocatable(Collection<String> serverIds) {
        return resourceServerAdmission.filterAllocatable(serverIds);
    }

    @Override
    public Map<String, String> findFrontlinesNeedingFailover() {
        return resourceServerAdmission.findFrontlinesNeedingFailover();
    }

    @Override
    public List<ResourceServerRespDTO> listLive() {
        return ResourceServerConvert.INSTANCE.toRespDTOList(
                resourceServerMapper.selectByLifecycleState(ResourceServerLifecycleEnum.LIVE.getState()));
    }
}
