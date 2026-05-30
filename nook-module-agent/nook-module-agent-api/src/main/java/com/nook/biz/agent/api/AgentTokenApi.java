package com.nook.biz.agent.api;

/**
 * Agent 鉴权 Token 跨模块 Api
 *
 * @author nook
 */
public interface AgentTokenApi {

    /**
     * 签发 agent 鉴权 token
     *
     * @return 64 char hex token
     */
    String generateToken();
}
