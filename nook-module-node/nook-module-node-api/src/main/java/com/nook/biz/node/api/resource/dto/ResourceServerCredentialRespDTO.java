package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 服务器 SSH 凭据 Response DTO
 *
 * @author nook
 */
@Data
public class ResourceServerCredentialRespDTO {

    /** 服务器编号. */
    private String serverId;

    /** SSH 端口. */
    private Integer sshPort;

    /** SSH 用户. */
    private String sshUser;

    /** SSH 密码. */
    private String sshPassword;

    /** SSH 连接超时 (秒). */
    private Integer sshTimeoutSeconds;

    /** SSH 命令执行超时 (秒). */
    private Integer sshOpTimeoutSeconds;

    /** SSH 文件上传超时 (秒). */
    private Integer sshUploadTimeoutSeconds;

    /** 装机超时 (秒). */
    private Integer installTimeoutSeconds;
}
