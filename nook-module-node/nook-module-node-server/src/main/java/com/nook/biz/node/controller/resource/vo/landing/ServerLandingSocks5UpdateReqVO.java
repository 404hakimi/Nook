package com.nook.biz.node.controller.resource.vo.landing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点 dante 配置更新 Request VO
 *
 * @author nook
 */
@Data
public class ServerLandingSocks5UpdateReqVO {

    /** SOCKS5 监听端口. */
    @NotNull(message = "socks5Port 不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer socks5Port;

    /** SOCKS5 认证用户名. */
    @Size(max = 64)
    private String socks5Username;

    /** SOCKS5 认证密码; 留空 = 保留原值. */
    @Size(max = 128)
    private String socks5Password;

    /** dante 日志级别. */
    @Size(max = 64)
    private String logLevel;

    /** dante logoutput 路径. */
    @Size(max = 255)
    private String logPath;

    /** 是否启用防火墙: 1=是 0=否. */
    @Min(value = 0) @Max(value = 1)
    private Integer firewallEnabled;

    /** dante 安装目录. */
    @Size(max = 255)
    private String installDir;
}
