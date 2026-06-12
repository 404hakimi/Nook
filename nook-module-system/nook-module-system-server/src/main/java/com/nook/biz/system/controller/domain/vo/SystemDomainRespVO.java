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

    /** 域名ID. */
    private String id;

    /** 根域名 (一级域名, 如 karsu.cc). */
    private String domain;

    /** Cloudflare Zone ID. */
    private String cfZoneId;

    /** Cloudflare API Token. */
    private String cfApiToken;

    /** 备注. */
    private String remark;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
