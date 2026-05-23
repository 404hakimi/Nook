package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台 - 服务器容量阈值 Update Request VO
 *
 * @author nook
 */
@Data
public class ResourceServerCapacityUpdateReqVO {

    /** 业务月流量阈值 GB; 0/null=不限. 同时是 throttle_state 90% 触发的基数. */
    @Min(value = 0)
    @Max(value = 1000000)
    private Integer monthlyTrafficGb;

    /** 业务限定带宽 Mbps; 0=不限. agent 跑 tc qdisc 真实 enforce. */
    @Min(value = 0)
    @Max(value = 100000)
    private Integer bandwidthLimitMbps;
}
