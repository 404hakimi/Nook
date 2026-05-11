package com.nook.biz.node.resource.controller.ip.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * IP 池新增 / 编辑统一入参; 业务校验 (类型存在 / IP 唯一) 由 ResourceIpPoolValidator 完成.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSaveReqVO {

    @NotBlank(message = "区域不能为空")
    @Size(max = 64)
    private String region;

    @NotBlank(message = "IP 类型不能为空")
    @Size(max = 36)
    private String ipTypeId;

    /** 部署模式: 1=self_deploy 2=external. */
    @NotNull(message = "部署模式不能为空")
    @Min(value = 1) @Max(value = 2)
    private Integer provisionMode;

    /** 出网真实 IP; 同时作为 SOCKS5 服务监听地址 (host = ipAddress, 不再单独存 socks5Host). */
    @NotBlank(message = "IP 地址不能为空")
    @Size(max = 45)
    private String ipAddress;

    @NotNull(message = "SOCKS5 端口不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer socks5Port;

    @Size(max = 64)
    private String socks5Username;

    /** SOCKS5 密码; Update 留空 = 保留原值 (不加 @NotBlank). */
    @Size(max = 255)
    private String socks5Password;

    /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded. */
    @NotNull(message = "状态不能为空")
    @Min(value = 1) @Max(value = 6)
    private Integer status;

    @Size(max = 255)
    private String remark;
}
