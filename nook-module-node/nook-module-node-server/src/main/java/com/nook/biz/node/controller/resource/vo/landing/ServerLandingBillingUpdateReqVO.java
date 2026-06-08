package com.nook.biz.node.controller.resource.vo.landing;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 管理后台 - SOCKS5 落地节点账面更新 Request VO
 *
 * @author nook
 */
@Data
public class ServerLandingBillingUpdateReqVO {

    /** 月成本 CNY. */
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal costMonthly;

    /** 账单日 1-28. */
    @Min(value = 1) @Max(value = 28)
    private Integer billingCycleDay;

    /** IP 到期日 YYYY-MM-DD. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;
}
