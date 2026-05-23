package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * IP 池 SSH 凭据 Response DTO (provision_mode=1 自部署用; 跨模块给 agent-server 装 landing).
 *
 * @author nook
 */
@Data
public class ResourceIpPoolCredentialRespDTO {

    private String ipId;

    /** SSH 主机; 留空 = ip_address 兜底. */
    private String sshHost;

    /** SSH 端口; 留空 = 22. */
    private Integer sshPort;

    /** SSH 用户; 留空 = root. */
    private String sshUser;

    private String sshPassword;
}
