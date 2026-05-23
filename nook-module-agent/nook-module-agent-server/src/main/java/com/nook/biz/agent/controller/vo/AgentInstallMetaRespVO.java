package com.nook.biz.agent.controller.vo;

import lombok.Data;

/**
 * 管理后台 - Agent 装机元信息 Response VO
 *
 * @author nook
 */
@Data
public class AgentInstallMetaRespVO {

    /** Backend 公网 URL (nook.agent.backend-public-url config 读); 前端可改. */
    private String backendUrl;

    /** Frontline + 选了 server 才填: xray binary 绝对路径 (xray_node 表读). */
    private String xrayBin;

    /** Frontline + 选了 server 才填: xray api server 端口 (loopback). */
    private Integer xrayApiPort;

    /** 选了 server 才填: resource_server.ssh_timeout_seconds. */
    private Integer sshTimeoutSeconds;

    /** 选了 server 才填: resource_server.ssh_op_timeout_seconds. */
    private Integer sshOpTimeoutSeconds;

    /** 选了 server 才填: resource_server.ssh_upload_timeout_seconds. */
    private Integer sshUploadTimeoutSeconds;

    /** 选了 server 才填: resource_server.install_timeout_seconds. */
    private Integer installTimeoutSeconds;

    /** Landing + 选了 ipId 才填: ip_pool.ip_address (host 兜底 + admin 展示). */
    private String ipAddress;
}
