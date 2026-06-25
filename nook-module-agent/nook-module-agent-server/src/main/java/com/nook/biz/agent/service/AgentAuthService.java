package com.nook.biz.agent.service;

import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;

/**
 * Agent 鉴权 Service 接口
 *
 * @author nook
 */
public interface AgentAuthService {

    /**
     * 校验 agent 上行鉴权 (token 不过线: 明文 serverId + 加密证明) 并获得对应服务器
     *
     * @param serverId  X-Agent-Server 头 (明文 serverId)
     * @param authProof X-Agent-Auth 头 (token 加密的鉴权证明)
     * @return 服务器信息; 校验失败抛 UNAUTHORIZED
     */
    ResourceServerRespDTO verifyAndGetServer(String serverId, String authProof);
}
