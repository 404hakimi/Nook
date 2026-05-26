package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点 SSH 凭据 Response VO
 *
 * @author nook
 */
@Data
public class ServerLandingCredentialRespVO {

    /** 落地节点编号 (== resource_server.id). */
    private String serverId;

    // SSH 主机 = resource_server.ip_address (canonical); 不在凭据 VO, 走详情聚合

    private Integer sshPort;

    private String sshUser;

    /** 明文 SSH 密码; 后台受信网络场景下发. */
    private String sshPassword;
}
