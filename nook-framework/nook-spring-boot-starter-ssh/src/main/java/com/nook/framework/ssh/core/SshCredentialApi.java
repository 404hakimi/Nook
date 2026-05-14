package com.nook.framework.ssh.core;

/**
 * SSH 凭据加载 SPI: framework 不查业务库, 由业务模块实现本接口把 serverId → SessionCredential 给回来.
 *
 * <p>业务模块 (如 nook-module-node) 必须提供一个 spring bean 实现本接口, 否则 {@link SshSessions}
 * 的按 serverId 路径无法工作 (会启动报错).
 *
 * @author nook
 */
public interface SshCredentialApi {

    /**
     * 按 serverId 加载 SSH 凭据; 业务侧典型实现是查 resource_server 表后转 SessionCredential.
     *
     * @param serverId 业务侧服务器标识
     * @return 凭据; 不存在时抛 BusinessException 或 IllegalArgumentException, 由 framework 透传到调用方
     */
    SessionCredential load(String serverId);
}
