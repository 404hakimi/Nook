package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点容量监控 Response VO
 *
 * @author nook
 */
@Data
public class ServerLandingCapacityRespVO {

    /** 落地节点编号 (== resource_server.id). */
    private String serverId;

    /** dante 实际限速 Mbps; 0=不限. */
    private Integer bandwidthLimitMbps;

    /** 月流量上限 GB; null/0=不限. */
    private Integer monthlyTrafficGb;

    /** 当周期累计已用字节 (agent push). */
    private Long usedTrafficBytes;

    /** 当周期下行字节 (vnstat rx 累计). */
    private Long rxBytes;

    /** 当周期上行字节 (vnstat tx 累计). */
    private Long txBytes;

    /** 周期重置策略 {@link ResourceServerQuotaResetPolicyEnum} */
    private String quotaResetPolicy;

    /** 按月流量重置日 1-28; FIXED 时为空. */
    private Integer resetDay;

    /** 限流状态 {@link ResourceServerThrottleStateEnum} */
    private String throttleState;
}
