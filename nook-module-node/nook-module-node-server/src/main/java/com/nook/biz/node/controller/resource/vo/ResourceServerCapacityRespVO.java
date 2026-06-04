package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - 服务器容量 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerCapacityRespVO {

    /** 服务器编号. */
    private String serverId;

    /** 机房 NIC 月配额 GB; 0/null = 不限. 同时是 throttle_state 90% 触发的基数 (业务阈值). */
    private Integer monthlyTrafficGb;

    /** 线路机出站带宽容量 Mbps; 供套餐分配不超卖(预留~10%), 0/空=不参与分配; 不做 tc 整形. */
    private Integer bandwidthLimitMbps;

    /** 当周期下行字节 (vnstat rx 累计). */
    private Long rxBytes;

    /** 当周期上行字节 (vnstat tx 累计). */
    private Long txBytes;

    /** 当周期已用流量字节 = rx + tx. */
    private Long usedTrafficBytes;

    /** 周期重置策略: MONTHLY / FIXED. */
    private String quotaResetPolicy;

    /** 按月流量重置日 1-28; FIXED 时为空. */
    private Integer resetDay;

    /** 限流状态: 正常 / 已触发限流. */
    private String throttleState;
}
