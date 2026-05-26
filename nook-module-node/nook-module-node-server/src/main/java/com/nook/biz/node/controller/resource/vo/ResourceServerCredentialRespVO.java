package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - 服务器 SSH 凭据 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerCredentialRespVO {

    private String serverId;
    private Integer sshPort;
    private String sshUser;

    /** 明文 SSH 密码; 后台运营受信网络下使用, UI 用 type=password 自然遮盖. */
    private String sshPassword;

    private Integer sshTimeoutSeconds;
    private Integer sshOpTimeoutSeconds;
    private Integer sshUploadTimeoutSeconds;
    private Integer installTimeoutSeconds;
}
