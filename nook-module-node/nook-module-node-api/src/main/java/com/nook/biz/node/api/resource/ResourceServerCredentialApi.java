package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;

import java.util.Collection;
import java.util.Map;

/**
 * 服务器 SSH 凭据 Api 接口
 *
 * @author nook
 */
public interface ResourceServerCredentialApi {

    /**
     * 取单 server 凭据 (SSH 装机 / 测连通性 / Cloudflare A 记录用).
     *
     * @param serverId server 主键
     * @return 凭据 DTO; 不存在返 null
     */
    ResourceServerCredentialRespDTO getByServerId(String serverId);

    /**
     * 取单 server 凭据; 缺失抛 SERVER_NOT_FOUND.
     *
     * @param serverId server 主键
     * @return 凭据 DTO
     */
    ResourceServerCredentialRespDTO requireByServerId(String serverId);

    /**
     * 批量查 (agent 列表展示 host 用).
     *
     * @param serverIds server 主键集合
     * @return key=serverId, value=凭据 DTO; 缺失 server 不在 map 里
     */
    Map<String, ResourceServerCredentialRespDTO> listByServerIds(Collection<String> serverIds);
}
