package com.nook.biz.node.controller.resource.vo.landing;

import com.nook.biz.node.api.enums.ResourceServerProvisionModeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点核心字段更新 Request VO
 *
 * @author nook
 */
@Data
public class ServerLandingCoreUpdateReqVO {

    /** 区域码. */
    @NotBlank(message = "区域不能为空")
    @Size(max = 32)
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    /** IP 类型ID. */
    @NotBlank(message = "IP 类型不能为空")
    private String ipTypeId;

    /** 出网 IP. */
    @NotBlank(message = "ipAddress 不能为空")
    @Size(max = 64)
    private String ipAddress;

    /** 部署模式 {@link ResourceServerProvisionModeEnum}; 取值由 Validator 校验. */
    @NotNull(message = "provisionMode 不能为空")
    private Integer provisionMode;

    /** 备注. */
    @Size(max = 512)
    private String remark;
}
