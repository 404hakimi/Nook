package com.nook.biz.resource.api.dto;

import lombok.Builder;

/**
 * 跨模块 DTO: 一台服务器的 SSH 接入凭据.
 *
 * <p>resource 模块从 resource_server 表读出, 其他模块 (node 等) 用它跑 SSH 命令.
 * 不再含 xray 相关字段 (那些放 xray_node 表, nook-biz-node 模块自管).
 *
 * @author nook
 */
@Builder
public record ServerCredentialDTO(
        String serverId,

        String sshHost,
        int sshPort,
        String sshUser,
        String sshPassword,
        int sshTimeoutSeconds
) {
}
