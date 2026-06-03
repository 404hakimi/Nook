package com.nook.biz.node.api.resource;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器运行时 Api 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerRuntimeApiImpl implements ResourceServerRuntimeApi {

    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;

    @Override
    public ResourceServerRuntimeRespDTO getByServerId(String serverId) {
        ResourceServerRuntimeDO row = resourceServerRuntimeMapper.selectById(serverId);
        return ObjectUtil.isNull(row) ? null : BeanUtils.toBean(row, ResourceServerRuntimeRespDTO.class);
    }

    @Override
    public Map<String, ResourceServerRuntimeRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(serverIds),
                ResourceServerRuntimeDO::getServerId,
                row -> BeanUtils.toBean(row, ResourceServerRuntimeRespDTO.class));
    }

    @Override
    public int onHeartbeat(String serverId, LocalDateTime at, String agentVersion, String clientIp) {
        return resourceServerRuntimeMapper.onHeartbeat(serverId, at, agentVersion, clientIp);
    }
}
