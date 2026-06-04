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

    /** IDC 服务商. */
    @Size(max = 64)
    private String idcProvider;

    /** 月成本 CNY. */
    private BigDecimal costMonthly;

    /** 账单日. */
    @Min(value = 1, message = "账单日 1-28")
    @Max(value = 28, message = "账单日 1-28")
    private Integer billingCycleDay;

    /** 到期时间. */
    private LocalDate expiresAt;
}
