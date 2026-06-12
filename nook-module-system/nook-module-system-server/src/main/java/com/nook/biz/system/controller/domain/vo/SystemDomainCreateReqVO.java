package com.nook.biz.system.controller.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 系统域名创建 Request VO
 *
 * @author nook
 */
@Data
public class SystemDomainCreateReqVO {

    /** 根域名 (一级域名, 如 karsu.cc). */
    @NotBlank(message = "根域名必填")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.[a-zA-Z]{2,}$",
            message = "请填一级域名 (如 karsu.cc), 不要带子域名")
    @Size(max = 255)
    private String domain;

    /** Cloudflare Zone ID. */
    @Size(max = 64)
    private String cfZoneId;

    /** Cloudflare API Token (DNS-01 签发 / 续期). */
    @Size(max = 255)
    private String cfApiToken;

    /** 备注. */
    @Size(max = 255)
    private String remark;
}
