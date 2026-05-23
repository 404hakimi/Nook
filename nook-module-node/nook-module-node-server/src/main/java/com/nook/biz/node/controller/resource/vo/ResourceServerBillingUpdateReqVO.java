package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 管理后台 - 服务器账面 Update Request VO (Create 流程嵌套在 ResourceServerCreateReqVO.billing 内复用)
 *
 * @author nook
 */
@Data
public class ResourceServerBillingUpdateReqVO {

    @Size(max = 64)
    private String idcProvider;

    @Min(value = 0)
    private Integer bandwidthMbps;

    private BigDecimal costMonthlyUsd;

    @Min(value = 1, message = "账单日 1-28")
    @Max(value = 28, message = "账单日 1-28")
    private Integer billingCycleDay;

    private LocalDate expiresAt;
}
