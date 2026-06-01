package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 服务器 NIC 流量配额 + 已用 RespDTO
 *
 * @author nook
 */
@Data
public class ResourceServerCapacityRespDTO {

    /** server 主键. */
    private String serverId;

    /** 机房 NIC 月配额 GB; 0/null = 不限. 同时是 throttle_state 90% 触发的基数 (业务阈值, 不是云厂商账面). */
    private Integer monthlyTrafficGb;

    /** 线路机出站接口实际限速 Mbps; 0=不限. agent 跑 tc qdisc 真实 enforce (业务阈值, 不是云厂商账面承诺). */
    private Integer bandwidthLimitMbps;

    /** 当周期下行字节. */
    private Long rxBytes;

    /** 当周期上行字节. */
    private Long txBytes;

    /** 当周期已用流量字节 = rx + tx. */
    private Long usedTrafficBytes;

    /** 限流状态 NORMAL / THROTTLED. */
    private String throttleState;
}
