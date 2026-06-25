package com.nook.biz.agent.service.impl;

import com.nook.biz.agent.service.AgentAuthService;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Agent 鉴权 Service 实现类; 验证 (按 serverId 找 token + 解密证明 + 校时间戳) 在 node 侧 (跟 AgentControlCrypto 同模块)
 *
 * @author nook
 */
@Service
public class AgentAuthServiceImpl implements AgentAuthService {

    @Resource
    private ResourceServerApi resourceServerApi;

    @Override
    public ResourceServerRespDTO verifyAndGetServer(String serverId, String authProof) {
        return resourceServerApi.verifyAgentAuth(serverId, authProof);
    }
}
