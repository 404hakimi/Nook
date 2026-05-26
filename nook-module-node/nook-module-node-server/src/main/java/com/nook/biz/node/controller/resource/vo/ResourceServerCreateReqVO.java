package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

    @NotBlank(message = "服务器别名不能为空")
    @Size(max = 64, message = "name 长度不能超过 64")
    private String name;

    /** 出网真实 IP / 域名 (= SSH 连接目标; canonical) */
    @NotBlank(message = "IP 地址不能为空")
    @Size(max = 128, message = "IP 地址长度不能超过 128")
    private String ipAddress;

    @NotBlank(message = "区域不能为空")
    @Size(max = 32, message = "区域码长度不能超过 32")
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    @Min(value = 0)
    private Integer totalIpCount;

    @Size(max = 512)
    private String remark;

    @NotBlank(message = "lifecycleState 不能为空")
    @Pattern(regexp = "INSTALLING|READY|LIVE|RETIRED", message = "lifecycleState 须为 INSTALLING/READY/LIVE/RETIRED")
    private String lifecycleState;

    /** SSH 凭据 (必填). */
    @NotNull(message = "credential 不能为空")
    @Valid
    private ResourceServerCredentialUpdateReqVO credential;

    /** 账面 (可空). */
    @Valid
    private ResourceServerBillingUpdateReqVO billing;

    /** 线路机扩展 (可空; LIVE 前置才必填 domain). */
    @Valid
    private ResourceServerFrontlineUpdateReqVO frontline;
}
