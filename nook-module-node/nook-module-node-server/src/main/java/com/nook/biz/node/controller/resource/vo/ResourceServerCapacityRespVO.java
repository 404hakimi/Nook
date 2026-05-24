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

    /** 机房 NIC 月配额 GB; 0/null = 不限. 同时是 throttle_state 90% 触发的基数 (业务阈值). */
    private Integer monthlyTrafficGb;

    /** 线路机出站接口实际限速 Mbps; 0=不限. agent 跑 tc qdisc 真实 enforce (业务阈值). */
    private Integer bandwidthLimitMbps;

    /** 单 server 客户端数硬上限; 0=不限. allocator 候选过滤 + xray inbound 客户数闸. */
    private Integer clientMaxCount;

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
