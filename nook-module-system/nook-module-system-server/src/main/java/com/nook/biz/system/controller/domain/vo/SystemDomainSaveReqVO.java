package com.nook.biz.system.controller.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 系统域名 保存 Request VO (创建 / 更新共用; 更新须带 id)
 *
 * @author nook
 */
@Data
public class SystemDomainSaveReqVO {

    /** 主键; 创建留空, 更新必填. */
    private String id;

    /** 域名 (FQDN). */
    @NotBlank(message = "域名必填")
    @Pattern(regexp = "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$", message = "域名格式非法")
    @Size(max = 255)
    private String domain;

    /** Cloudflare Zone ID. */
    @Size(max = 64)
    private String cfZoneId;

    /** Cloudflare DNS A 记录 ID. */
    @Size(max = 64)
    private String cfRecordId;

    /** Cloudflare API Token (DNS-01 签发 / 续期). */
    @Size(max = 255)
    private String cfApiToken;

    /** 备注. */
    @Size(max = 255)
    private String remark;
}
