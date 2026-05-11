package com.nook.biz.resource.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * IP 池条目跨模块出参; 业务模块兑换 IP 后用它配 SOCKS5 outbound 上 Xray。
 * 故意带 socks5 密码原文 (在 nook 内部传递, 不下发前端)。
 */
@Data
@Builder
public class IpPoolEntryDTO {

    private String id;
    private String region;
    private String ipTypeId;
    /** 出网真实 IP; 同时作为 SOCKS5 服务监听地址 (host = ipAddress). */
    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;
    private String socks5Password;
}
