package com.nook.biz.node.controller.resource.vo;

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

    @NotNull(message = "socks5Port 不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer socks5Port;

    @Size(max = 64)
    private String socks5Username;

    /** 留空 = 保留原值. */
    @Size(max = 128)
    private String socks5Password;

    @Size(max = 64)
    private String logLevel;

    /** dante logoutput 路径. */
    @Size(max = 255)
    private String logPath;

    @Min(value = 0) @Max(value = 1)
    private Integer autostartEnabled;

    @Min(value = 0) @Max(value = 1)
    private Integer firewallEnabled;

    @Size(max = 255)
    private String installDir;
}
