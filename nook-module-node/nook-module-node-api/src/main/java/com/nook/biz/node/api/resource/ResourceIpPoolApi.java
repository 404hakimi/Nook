package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceIpPoolRespDTO;

/**
 * 资源 IP 池 Api 接口 (跨模块导出, agent-server landing 装机 / token 鉴权用).
 *
 * @author nook
 */
public interface ResourceIpPoolApi {

    /**
     * 校验 IP 池行存在, 返完整 DTO; 不存在抛 BusinessException(IP_POOL_NOT_FOUND).
     *
     * @param ipId resource_ip_pool.id
     * @return IP 池视图
     */
    ResourceIpPoolRespDTO validateExists(String ipId);

    /**
     * 按 agent_token 查 IP 池行 (landing agent push 接口鉴权用); 找不到返 null.
     *
     * @param agentToken X-Agent-Token header 值
     * @return IP 池视图; 未匹配返 null
     */
    ResourceIpPoolRespDTO getByAgentToken(String agentToken);
}
