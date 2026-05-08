package com.nook.biz.resource.api.dto;

import lombok.Builder;

/**
 * 跨模块 DTO：一台服务器的接入凭据。
 * Resource 模块从 resource_server 表读出来后构造这个 DTO，xray 模块通过它生成 backend。
 *
 * 字段视 backendType 取舍：
 *   "threexui"  → 必填 panelBaseUrl / panelUsername / panelPassword
 *   "xray-grpc" → 必填 xrayGrpcHost / xrayGrpcPort
 *   SSH 字段双 backend 都用得到（运维命令、SSH 隧道）
 *
 * backendType 用 String 而不是枚举，避免 resource 反向依赖 xray 模块。
 */
@Builder
public record ServerCredentialDTO(
        String serverId,
        String backendType,

        String sshHost,
        int sshPort,
        String sshUser,
        String sshPassword,
        String sshPrivateKey,
        String sshPrivateKeyPassphrase,
        /** SSH 命令最大耗时(秒)；0/null 走 SshExecutor 内置默认 */
        Integer sshTimeoutSeconds,

        // 3x-ui 面板
        String panelBaseUrl,
        String panelUsername,
        String panelPassword,
        boolean panelIgnoreTls,

        // Xray 原生 gRPC
        String xrayGrpcHost,
        Integer xrayGrpcPort,
        String xrayGrpcAuthToken,

        /** backend HTTP/gRPC 调用超时(秒)；0/null 走兜底 */
        Integer backendTimeoutSeconds
) {
    /** 默认 20s 起步；HTTP/TLS 跨洲常态 5-15s，给 20s 余量。 */
    public int backendTimeoutSecondsOrDefault() {
        return (backendTimeoutSeconds == null || backendTimeoutSeconds <= 0) ? 20 : backendTimeoutSeconds;
    }
}
