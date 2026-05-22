package com.nook.biz.agent.controller.vo;

import lombok.Data;

/**
 * Agent 装机 meta: 只返 "backend 已知数据" (config / DB), 前端拿去 prefill 表单.
 * 路径默认由前端持有 (装机根目录 / binary / config / systemd unit), 此处不下发.
 */
@Data
public class AgentInstallMetaRespVO {

    /** Backend 公网 URL (nook.backend.public-url config 读); 前端可改. */
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
}
