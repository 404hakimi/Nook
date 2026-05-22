package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;

import java.util.List;

/**
 * 资源服务器 Api 接口
 *
 * @author nook
 */
public interface ResourceServerApi {

    /**
     * 校验 server 存在, 返完整 DTO; 不存在抛 BusinessException(SERVER_NOT_FOUND).
     *
     * @param serverId server 主键
     * @return server 视图
     */
    ResourceServerRespDTO validateExists(String serverId);

    /**
     * 按 agent_token 查 server; 不存在返 null (调用方自己抛 UNAUTHORIZED).
     *
     * @param agentToken X-Agent-Token header 值
     * @return server 视图; 未匹配返 null
     */
    ResourceServerRespDTO getByAgentToken(String agentToken);

    /**
     * 拉所有未软删的 server (agent 列表展示用).
     *
     * @return server 列表; 空表返空 list
     */
    List<ResourceServerRespDTO> listAll();
}
