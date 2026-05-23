package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 资源 IP 池 Response DTO (主表核心字段; SSH 凭据 / 账面 / dante 各走自己的 Api).
 *
 * @author nook
 */
@Data
public class ResourceIpPoolRespDTO {

    private String id;

    /** 区域码 (FK → resource_region.code). */
    private String region;

    /** 关联 resource_ip_type.id. */
    private String ipTypeId;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址. */
    private String ipAddress;

    /** lifecycle 状态 (INSTALLING / READY / LIVE / RETIRED). */
    private String lifecycleState;

    /** 部署模式 (1=自部署 SSH 装 dante / 2=第三方 SOCKS5). */
    private Integer provisionMode;

    /** Agent 鉴权 token; landing 装机一次性 SHA256 生成. */
    private String agentToken;

    private String remark;
}
