package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - 服务器 SSH 凭据 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerCredentialRespVO {

    /** 服务器编号. */
    private String serverId;
    /** SSH 端口. */
    private Integer sshPort;
    /** SSH 用户名. */
    private String sshUser;

    /** 明文 SSH 密码; 后台运营受信网络下使用, UI 用 type=password 自然遮盖. */
    private String sshPassword;

    /** SSH 连接超时秒数. */
    private Integer sshTimeoutSeconds;
    /** SSH 命令执行超时秒数. */
    private Integer sshOpTimeoutSeconds;
    /** SSH 上传超时秒数. */
    private Integer sshUploadTimeoutSeconds;
    /** 装机超时秒数. */
    private Integer installTimeoutSeconds;
}
