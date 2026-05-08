package com.nook.biz.resource.api.dto;

import lombok.Builder;

/**
 * 跨模块 DTO：一台中转线路服务器的接入凭据。
 * Resource 模块从 resource_server 表读出来后构造，xray 模块通过它生成 backend。
 *
 * 字段:
 *   SSH 凭据  → 远程运维(部署/重启/拉日志)、SSH 隧道转发 gRPC
 *   xrayGrpc* → Xray 内核 gRPC API 地址(通常 127.0.0.1, 经 SSH 隧道访问)
 */
@Builder
public record ServerCredentialDTO(
        String serverId,

        String sshHost,
        int sshPort,
        String sshUser,
        String sshPassword,
        String sshPrivateKey,
        String sshPrivateKeyPassphrase,
        Integer sshTimeoutSeconds,

        String xrayGrpcHost,
        Integer xrayGrpcPort,

        Integer backendTimeoutSeconds
) {
    /** SSH 命令最大耗时秒数；缺省走 SshExecutor 内部 fallback. */
    public Integer sshTimeoutSecondsValue() {
        return sshTimeoutSeconds;
    }

    /** backend gRPC 调用超时；0/null 走 20s 兜底. */
    public int backendTimeoutSecondsOrDefault() {
        return (backendTimeoutSeconds == null || backendTimeoutSeconds <= 0) ? 20 : backendTimeoutSeconds;
    }
}
