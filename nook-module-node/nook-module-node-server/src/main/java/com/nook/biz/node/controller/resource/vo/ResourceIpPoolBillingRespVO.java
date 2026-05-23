package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 管理后台 - IP 池账面 Response VO
 *
 * @author nook
 */
@Data
public class ResourceIpPoolBillingRespVO {

    private String ipId;
    private Integer bandwidthMbps;
    private Integer trafficQuotaGb;
    private BigDecimal costMonthlyUsd;
    private Integer billingCycleDay;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;
}
