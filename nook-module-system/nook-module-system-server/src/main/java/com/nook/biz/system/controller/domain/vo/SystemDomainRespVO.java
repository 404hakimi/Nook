package com.nook.biz.system.controller.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 系统域名 Response VO
 *
 * @author nook
 */
@Data
public class SystemDomainRespVO {

    private String id;

    /** 域名 (FQDN). */
    private String domain;

    /** Cloudflare Zone ID. */
    private String cfZoneId;

    /** Cloudflare DNS A 记录 ID. */
    private String cfRecordId;

    /** Cloudflare API Token. */
    private String cfApiToken;

    /** 备注. */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
