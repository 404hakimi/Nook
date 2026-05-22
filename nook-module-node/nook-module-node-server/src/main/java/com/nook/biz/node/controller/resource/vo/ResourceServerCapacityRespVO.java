package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - 服务器容量 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerCapacityRespVO {

    private String serverId;

    /** 机房 NIC 月配额 GB; 0/null = 不限. */
    private Integer monthlyTrafficGb;

    /** 当周期下行字节 (vnstat rx 累计). */
    private Long rxBytes;

    /** 当周期上行字节 (vnstat tx 累计). */
    private Long txBytes;

    /** 当周期已用流量字节 = rx + tx. */
    private Long usedTrafficBytes;

    /** 周期重置策略: CALENDAR_MONTH / BILLING_CYCLE / FIXED. */
    private String quotaResetPolicy;

    /** NORMAL / THROTTLED. */
    private String throttleState;
}
