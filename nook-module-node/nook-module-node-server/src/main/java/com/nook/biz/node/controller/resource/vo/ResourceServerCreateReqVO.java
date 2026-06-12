package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 服务器创建 Request VO
 *
 * @author nook
 */
@Data
public class ResourceServerCreateReqVO {

    /** 服务器类型 (frontline / landing); 取值范围 Validator 走 ResourceServerTypeEnum 校验. */
    @NotBlank(message = "serverType 不能为空")
    private String serverType;

    /** 服务器别名 (用户填写); 全局唯一. */
    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过 64")
    private String name;

    /** 出网真实 IP / 域名 (即 SSH 连接目标). */
    @NotBlank(message = "IP 地址不能为空")
    @Size(max = 128, message = "IP 地址长度不能超过 128")
    private String ipAddress;

    /** 区域码. */
    @NotBlank(message = "区域不能为空")
    @Size(max = 32, message = "区域码长度不能超过 32")
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    /** IP 类型ID; 落地机必填, 线路机不传. */
    @Size(max = 32, message = "ipTypeId 长度不能超过 32")
    private String ipTypeId;

    /** 备注. */
    @Size(max = 512)
    private String remark;

    /** 装机生命周期; 取值范围 Validator 走 ResourceServerLifecycleEnum 校验. */
    @NotBlank(message = "lifecycleState 不能为空")
    private String lifecycleState;

    /** SSH 凭据 (必填; 装机需要). */
    @NotNull(message = "credential 不能为空")
    @Valid
    private ResourceServerCredentialUpdateReqVO credential;
}
