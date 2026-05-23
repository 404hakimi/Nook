package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - IP 池 dante 配置 + 限速 Update Request VO (Create 流程嵌套在 SaveReqVO 内复用)
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSocks5UpdateReqVO {

    @NotNull(message = "SOCKS5 端口不能为空")
    @Min(value = 1, message = "SOCKS5 端口范围 1-65535")
    @Max(value = 65535, message = "SOCKS5 端口范围 1-65535")
    private Integer socks5Port;

    @Size(max = 64)
    private String socks5Username;

    /** Update 留空 = 保留原值. */
    @Size(max = 255)
    private String socks5Password;

    @Size(max = 64)
    private String logLevel;

    @NotBlank(message = "logPath 必填")
    @Size(max = 255)
    private String logPath;

    @Min(value = 0) @Max(value = 1)
    private Integer autostartEnabled;

    @Min(value = 0) @Max(value = 1)
    private Integer firewallEnabled;

    @Size(max = 255)
    private String firewallAllowFrom;

    @Size(max = 255)
    private String installDir;

    /** dante 实际限速 Mbps; 0=不限. */
    @Min(value = 0, message = "限速不能为负")
    @Max(value = 1_000_000)
    private Integer bandwidthLimitMbps;
}
