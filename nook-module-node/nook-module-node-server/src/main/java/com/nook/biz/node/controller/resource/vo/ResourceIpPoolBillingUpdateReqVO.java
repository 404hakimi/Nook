package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 管理后台 - IP 池账面 Update Request VO (Create 流程嵌套在 SaveReqVO 内复用)
 *
 * @author nook
 */
@Data
public class ResourceIpPoolBillingUpdateReqVO {

    /** 采购带宽上限 Mbps; 仅账面记录, 不参与 enforce. */
    @Min(value = 1) @Max(value = 1_000_000)
    private Integer bandwidthMbps;

    /** 采购流量上限 GB; 仅账面记录. */
    @Min(value = 1) @Max(value = 10_000_000)
    private Integer trafficQuotaGb;

    private BigDecimal costMonthlyUsd;

    @Min(value = 1, message = "账单日 1-28")
    @Max(value = 28, message = "账单日 1-28")
    private Integer billingCycleDay;

    private LocalDate expiresAt;
}
