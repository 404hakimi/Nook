package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/** Server NIC 流量配额 + 已用 (rx/tx 拆开 + 双向合计). */
@Data
public class ResourceServerCapacityRespDTO {

    /** server 主键. */
    private String serverId;

    /** 机房 NIC 月配额 GB; 0/null = 不限. */
    private Integer monthlyTrafficGb;

    /** 当周期下行字节. */
    private Long rxBytes;

    /** 当周期上行字节. */
    private Long txBytes;

    /** 当周期已用流量字节 = rx + tx. */
    private Long usedTrafficBytes;

    /** 限流状态 NORMAL / THROTTLED. */
    private String throttleState;
}
