package com.nook.biz.node.resource.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.resource.controller.server.vo.ResourceServerPageReqVO;
import com.nook.biz.node.resource.controller.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.node.resource.dto.ServerBriefDTO;
import com.nook.biz.node.resource.entity.ResourceServer;
import com.nook.biz.node.resource.event.ServerCredentialChangedEvent;
import com.nook.biz.node.resource.mapper.ResourceServerMapper;
import com.nook.biz.node.resource.service.ResourceServerService;
import com.nook.biz.node.resource.validator.ResourceServerValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResourceServerServiceImpl implements ResourceServerService {

    private final ResourceServerMapper resourceServerMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ResourceServerValidator serverValidator;

    @Override
    public ResourceServer findById(String id) {
        return serverValidator.validateExists(id);
    }

    @Override
    public boolean exists(String id) {
        return resourceServerMapper.selectById(id) != null;
    }

    @Override
    public PageResult<ResourceServer> page(ResourceServerPageReqVO reqVO) {
        IPage<ResourceServer> result = resourceServerMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public ResourceServer create(ResourceServerSaveReqVO reqVO) {
        serverValidator.validateNameUnique(null, reqVO.getName());
        serverValidator.validateHostUnique(null, reqVO.getHost());

        ResourceServer entity = BeanUtils.toBean(reqVO, ResourceServer.class);
        resourceServerMapper.insert(entity);
        return entity;
    }

    @Override
    public void update(String id, ResourceServerSaveReqVO reqVO) {
        serverValidator.validateExists(id);
        serverValidator.validateNameUnique(id, reqVO.getName());
        serverValidator.validateHostUnique(id, reqVO.getHost());

        ResourceServer entity = BeanUtils.toBean(reqVO, ResourceServer.class);
        resourceServerMapper.update(entity, Wrappers.<ResourceServer>lambdaUpdate().eq(ResourceServer::getId, id));
        // 凭据变更事件: SshSessionManager 据此清掉该 server 的 SSH 会话缓存, 下次调用走新凭据
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public void delete(String id) {
        serverValidator.validateExists(id);
        resourceServerMapper.deleteById(id);
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public Map<String, ServerBriefDTO> loadBriefMap(Collection<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) return Collections.emptyMap();
        Set<String> dedup = new HashSet<>();
        for (String id : serverIds) {
            if (id != null && !id.isEmpty()) dedup.add(id);
        }
        if (dedup.isEmpty()) return Collections.emptyMap();
        List<ResourceServer> rows = resourceServerMapper.selectBatchIds(dedup);
        Map<String, ServerBriefDTO> out = new HashMap<>(rows.size() * 2);
        for (ResourceServer e : rows) {
            out.put(e.getId(), ServerBriefDTO.builder()
                    .serverId(e.getId())
                    .name(e.getName())
                    .host(e.getHost())
                    .build());
        }
        return out;
    }
}
