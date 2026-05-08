package com.nook.biz.resource.api.dto;

import lombok.Builder;

/**
 * 跨模块 DTO: 一台中转线路服务器的接入凭据。
 * Resource 模块从 resource_server 表读出, xray 模块用它生成 backend / 跑 SSH 命令。
 *
 * <p>所有数值字段均为 DB NOT NULL 列, 不允许 null;  调用方拿到这个 DTO 后可直接使用 — 业务侧不再做空值兜底。
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
        int sshTimeoutSeconds,

        String xrayGrpcHost,
        int xrayGrpcPort,

        int backendTimeoutSeconds
) {
}
