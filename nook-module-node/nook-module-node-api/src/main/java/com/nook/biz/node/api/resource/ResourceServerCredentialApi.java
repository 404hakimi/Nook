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
     * 取单台服务器凭据
     *
     * @param serverId 服务器ID
     * @return 凭据 DTO; 不存在返 null
     */
    ResourceServerCredentialRespDTO getByServerId(String serverId);

    /**
     * 取单台服务器凭据; 缺失则抛业务异常
     *
     * @param serverId 服务器ID
     * @return 凭据 DTO
     */
    ResourceServerCredentialRespDTO requireByServerId(String serverId);

    /**
     * 批量查服务器凭据
     *
     * @param serverIds 服务器ID集合
     * @return 服务器ID → 凭据 DTO (缺失的不在 map 内)
     */
    Map<String, ResourceServerCredentialRespDTO> listByServerIds(Collection<String> serverIds);
}
