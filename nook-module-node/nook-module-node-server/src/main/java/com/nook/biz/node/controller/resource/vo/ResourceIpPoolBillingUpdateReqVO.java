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

    private BigDecimal costMonthlyUsd;

    @Min(value = 1, message = "账单日 1-28")
    @Max(value = 28, message = "账单日 1-28")
    private Integer billingCycleDay;

    private LocalDate expiresAt;
}
