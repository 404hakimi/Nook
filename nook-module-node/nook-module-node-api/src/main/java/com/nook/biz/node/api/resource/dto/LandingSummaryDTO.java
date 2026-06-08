package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 落地机概要 (主表 lifecycle + landing 子表 ipType); 供 trade 算 SKU 池容量 + 绑定校验.
 *
 * @author nook
 */
@Data
public class LandingSummaryDTO {

    /** 落地节点编号. */
    private String serverId;

    /** 装机生命周期: 装机中 / 待上线 / 运行中 / 已退役. */
    private String lifecycleState;

    /** landing 子表 ip_type_id. */
    private String ipTypeId;

    /** 主表出网 IP. */
    private String ipAddress;
}
