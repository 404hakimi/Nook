package com.nook.biz.node.resource.service.impl;

import com.nook.biz.node.resource.api.ResourceServerApi;
import com.nook.biz.node.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.resource.mapper.ResourceServerMapper;
import com.nook.biz.node.resource.service.ResourceServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceServerApiImpl implements ResourceServerApi {

    private final ResourceServerService resourceServerService;
    private final ResourceServerMapper resourceServerMapper;

    @Override
    public ServerCredentialDTO loadCredential(String serverId) {
        return resourceServerService.loadCredential(serverId);
    }

    @Override
    public boolean exists(String serverId) {
        return resourceServerMapper.selectById(serverId) != null;
    }
}
