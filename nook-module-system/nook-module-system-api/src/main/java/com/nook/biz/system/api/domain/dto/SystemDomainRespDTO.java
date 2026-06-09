package com.nook.biz.system.api.domain.dto;

import lombok.Data;

/**
 * 跨模块 - 系统域名 Response DTO
 *
 * @author nook
 */
@Data
public class SystemDomainRespDTO {

    private String id;

    /** 根域名 (一级域名, 如 karsu.cc). */
    private String domain;

    /** Cloudflare Zone ID. */
    private String cfZoneId;

    /** Cloudflare API Token (装机 DNS-01 签发 / 续期用). */
    private String cfApiToken;
}
