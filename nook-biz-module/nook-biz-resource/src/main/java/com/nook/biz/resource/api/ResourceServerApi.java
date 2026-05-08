package com.nook.biz.resource.api;

import com.nook.biz.resource.api.dto.ServerCredentialDTO;

/**
 * Resource 模块对外暴露的服务器查询接口。
 * 跨模块依赖只允许 import 这个 api 包下的类型，不许碰 service / mapper / entity。
 */
public interface ResourceServerApi {

    /** 加载凭据；server 不存在抛 BusinessException(SERVER_NOT_FOUND)。 */
    ServerCredentialDTO loadCredential(String serverId);

    /** 仅探测存在性，不抛错。 */
    boolean exists(String serverId);
}
