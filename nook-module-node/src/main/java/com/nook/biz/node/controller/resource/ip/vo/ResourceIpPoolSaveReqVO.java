package com.nook.biz.node.controller.resource.ip.vo;

import com.nook.biz.node.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.enums.ResourceIpPoolStatusEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * IP 池新增 / 编辑 Request VO.
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

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum} */
    @NotNull(message = "部署模式不能为空")
    @Min(value = 1, message = "部署模式值越界")
    @Max(value = 2, message = "部署模式值越界")
    private Integer provisionMode;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址 */
    @NotBlank(message = "IP 地址不能为空")
    @Size(max = 45)
    private String ipAddress;

    @NotNull(message = "SOCKS5 端口不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer socks5Port;

    @Size(max = 64)
    private String socks5Username;

    /** Update 留空 = 保留原值, 故不加 @NotBlank. */
    @Size(max = 255)
    private String socks5Password;

    /** 状态; 取值见 {@link ResourceIpPoolStatusEnum} */
    @NotNull(message = "状态不能为空")
    @Min(value = 1, message = "状态值越界")
    @Max(value = 6, message = "状态值越界")
    private Integer status;

    @Size(max = 255)
    private String remark;
}
