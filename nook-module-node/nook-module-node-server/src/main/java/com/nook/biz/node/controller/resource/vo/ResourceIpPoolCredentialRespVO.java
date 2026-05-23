package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - IP 池 SSH 凭据 Response VO
 *
 * @author nook
 */
@Data
public class ResourceIpPoolCredentialRespVO {

    private String ipId;
    private String sshHost;
    private Integer sshPort;
    private String sshUser;

    /** 明文 SSH 密码; 后台运营受信网络下使用, UI 用 type=password 自然遮盖. */
    private String sshPassword;
}
