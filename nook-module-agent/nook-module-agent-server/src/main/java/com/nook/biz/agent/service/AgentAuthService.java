package com.nook.biz.agent.service;

import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;

/**
 * Agent 鉴权 Service 接口
 *
 * @author nook
 */
public interface AgentAuthService {

    /**
     * 校验 agent token 并获得对应服务器
     *
     * @param agentToken X-Agent-Token 请求头值
     * @return 服务器信息
     */
    ResourceServerRespDTO verifyAndGetServer(String agentToken);
}
