package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点 dante 配置 Response VO
 *
 * @author nook
 */
@Data
public class ServerLandingSocks5RespVO {

    /** 落地节点编号. */
    private String serverId;

    /** SOCKS5 端口. */
    private Integer socks5Port;
    /** SOCKS5 用户名. */
    private String socks5Username;

    /** 明文密码 (后台受信网络场景下发). */
    private String socks5Password;

    /** dante 日志关键字组合. */
    private String logLevel;

    /** dante logoutput 路径. */
    private String logPath;

    /** systemd 开机自启 1/0. */
    private Integer autostartEnabled;

    /** 部署时是否配 UFW 1/0. */
    private Integer firewallEnabled;

    /** SOCKS5 安装目录. */
    private String installDir;
}
