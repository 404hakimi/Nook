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

    /** 域名 (FQDN). */
    private String domain;

    /** Cloudflare Zone ID. */
    private String cfZoneId;

    /** Cloudflare DNS A 记录 ID. */
    private String cfRecordId;

    /** Cloudflare API Token (装机 DNS-01 签发 / 续期用). */
    private String cfApiToken;
}
