package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - IP 池容量监控 Response VO
 *
 * @author nook
 */
@Data
public class ResourceIpPoolCapacityRespVO {

    private String ipId;
    private Integer bandwidthLimitMbps;
    private Integer monthlyTrafficGb;
    private Long usedTrafficBytes;
    private Long rxBytes;
    private Long txBytes;
    private String quotaResetPolicy;
    private String throttleState;
}
