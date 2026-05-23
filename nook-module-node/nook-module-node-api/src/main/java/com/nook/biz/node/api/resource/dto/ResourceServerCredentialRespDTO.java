package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 服务器 SSH 凭据 Response DTO
 *
 * @author nook
 */
@Data
public class ResourceServerCredentialRespDTO {

    private String serverId;

    private String host;

    private Integer sshPort;

    private String sshUser;

    private String sshPassword;

    private Integer sshTimeoutSeconds;

    private Integer sshOpTimeoutSeconds;

    private Integer sshUploadTimeoutSeconds;

    private Integer installTimeoutSeconds;
}
