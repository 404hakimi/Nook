package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceIpPoolCredentialRespDTO;

/**
 * IP 池 SSH 凭据 Api 接口 (跨模块导出, agent-server landing 装机用).
 *
 * @author nook
 */
public interface ResourceIpPoolCredentialApi {

    /**
     * 取单 IP 凭据 (provision_mode=1 自部署 dante 用).
     *
     * @param ipId resource_ip_pool.id
     * @return 凭据 DTO; 不存在返 null
     */
    ResourceIpPoolCredentialRespDTO getByIpId(String ipId);

    /**
     * 取单 IP 凭据; 缺失抛 IP_POOL_NOT_FOUND.
     *
     * @param ipId resource_ip_pool.id
     * @return 凭据 DTO
     */
    ResourceIpPoolCredentialRespDTO requireByIpId(String ipId);
}
