package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - IP 池 dante 配置 + 限速 Response VO
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSocks5RespVO {

    private String ipId;
    private Integer socks5Port;
    private String socks5Username;

    /** 明文 SOCKS5 密码; 后台运营受信网络下使用, UI 用 type=password 自然遮盖. */
    private String socks5Password;
    private String logLevel;
    private String logPath;
    private Integer autostartEnabled;
    private Integer firewallEnabled;
    private String firewallAllowFrom;
    private String installDir;

    /** dante 实际限速 Mbps; 0=不限. */
    private Integer bandwidthLimitMbps;
}
