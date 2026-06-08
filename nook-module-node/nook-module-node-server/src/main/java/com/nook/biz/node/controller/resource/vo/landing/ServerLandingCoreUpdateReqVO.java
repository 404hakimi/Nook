package com.nook.biz.node.controller.resource.vo.landing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @NotBlank(message = "区域不能为空")
    @Size(max = 32)
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    @NotBlank(message = "IP 类型不能为空")
    private String ipTypeId;

    @NotBlank(message = "ipAddress 不能为空")
    @Size(max = 64)
    private String ipAddress;

    @NotNull(message = "provisionMode 不能为空")
    @Min(value = 1) @Max(value = 2)
    private Integer provisionMode;

    @Size(max = 512)
    private String remark;
}
