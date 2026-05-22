package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/** Server NIC 流量配额 + 已用 (双向累计). */
@Data
public class ResourceServerCapacityRespDTO {

    /** server 主键. */
    private String serverId;

    /** 机房 NIC 月配额 GB; 0/null = 不限. */
    private Integer monthlyTrafficGb;

    /** 当周期已用流量字节. */
    private Long usedTrafficBytes;

    /** 限流状态 NORMAL / THROTTLED. */
    private String throttleState;
}
