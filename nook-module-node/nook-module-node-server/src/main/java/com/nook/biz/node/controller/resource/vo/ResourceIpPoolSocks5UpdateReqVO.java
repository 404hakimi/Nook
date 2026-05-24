package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - IP 池 dante 配置 + 限速 Update Request VO
 *
 * <p>装机产物 (installDir / logPath / autostart / firewall) 拆到 install 子表 + InstallUpdateReqVO.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSocks5UpdateReqVO {

    @NotNull(message = "SOCKS5 端口不能为空")
    @Min(value = 1, message = "SOCKS5 端口范围 1-65535")
    @Max(value = 65535, message = "SOCKS5 端口范围 1-65535")
    private Integer socks5Port;

    @NotBlank(message = "socks5Username 必填")
    @Size(max = 64)
    private String socks5Username;

    /** Update 留空 = 保留原值. */
    @Size(max = 255)
    private String socks5Password;

    @NotBlank(message = "logLevel 必填; 例 'connect disconnect error'")
    @Size(max = 64)
    private String logLevel;

    /** dante 实际限速 Mbps; 0=不限. */
    @NotNull(message = "bandwidthLimitMbps 必填; 0=不限")
    @Min(value = 0, message = "限速不能为负")
    @Max(value = 1_000_000)
    private Integer bandwidthLimitMbps;
}
