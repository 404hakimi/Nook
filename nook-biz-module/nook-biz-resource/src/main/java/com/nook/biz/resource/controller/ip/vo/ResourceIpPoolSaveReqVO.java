package com.nook.biz.resource.controller.ip.vo;

import com.nook.common.web.validation.Create;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * IP 池新增/编辑统一入参。
 * Create 必填: region / ipTypeId / ipAddress / socks5Host / socks5Port / socks5Username / socks5Password
 * Update 全部可选(传啥改啥); socks5Password 留空 = 保持原值.
 */
@Data
public class ResourceIpPoolSaveReqVO {

    @NotBlank(message = "区域不能为空", groups = Create.class)
    @Size(max = 64)
    private String region;

    @NotBlank(message = "IP 类型不能为空", groups = Create.class)
    @Size(max = 36)
    private String ipTypeId;

    @NotBlank(message = "IP 地址不能为空", groups = Create.class)
    @Size(max = 64)
    private String ipAddress;

    @NotBlank(message = "SOCKS5 主机不能为空", groups = Create.class)
    @Size(max = 128)
    private String socks5Host;

    @NotNull(message = "SOCKS5 端口不能为空", groups = Create.class)
    @Min(value = 1, message = "SOCKS5 端口最小 1")
    @Max(value = 65535, message = "SOCKS5 端口最大 65535")
    private Integer socks5Port;

    @Size(max = 64)
    private String socks5Username;

    /** 编辑时留空 = 保持原值。 */
    @Size(max = 255)
    private String socks5Password;

    /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded; null = 默认 1。 */
    private Integer status;

    private BigDecimal score;

    private Integer scamalyticsScore;

    private Integer ipqsScore;

    @Size(max = 512)
    private String remark;
}
