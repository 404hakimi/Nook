package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 落地机概要 (主表 lifecycle + landing 子表 status/ipType); 供 trade 算 SKU 池容量 + 绑定校验.
 *
 * @author nook
 */
@Data
public class LandingSummaryDTO {

    private String serverId;

    /** 主表装机生命周期 INSTALLING/READY/LIVE/RETIRED. */
    private String lifecycleState;

    /** 落地机占用状态 (可分配 / 已占用 / 预留); null=非落地机或无子表. */
    private String status;

    /** landing 子表 ip_type_id. */
    private String ipTypeId;

    /** 主表出网 IP. */
    private String ipAddress;
}
