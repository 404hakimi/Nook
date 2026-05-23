package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.api.enums.ResourceIpPoolProvisionModeEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - IP 池核心字段更新 Request VO (lifecycle 走专门 transition 接口)
 *
 * @author nook
 */
@Data
public class ResourceIpPoolCoreUpdateReqVO {

    @NotBlank(message = "区域不能为空")
    @Size(max = 32, message = "区域码长度不能超过 32")
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    @NotBlank(message = "IP 类型不能为空")
    @Size(max = 36)
    private String ipTypeId;

    @NotBlank(message = "IP 地址不能为空")
    @Size(max = 45)
    private String ipAddress;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum}. */
    @NotNull(message = "部署模式不能为空")
    @Min(value = 1, message = "部署模式值越界")
    @Max(value = 2, message = "部署模式值越界")
    private Integer provisionMode;

    @Size(max = 255)
    private String remark;
}
