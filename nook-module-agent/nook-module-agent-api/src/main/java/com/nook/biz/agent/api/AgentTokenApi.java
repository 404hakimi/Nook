package com.nook.biz.agent.api;

/**
 * Agent 鉴权 Token 跨模块 Api (供其它模块创建 server 时签发 token)
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
