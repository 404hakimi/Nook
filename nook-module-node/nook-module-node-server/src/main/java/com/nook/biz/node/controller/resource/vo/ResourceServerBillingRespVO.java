package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 管理后台 - 服务器账面 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerBillingRespVO {

    /** 服务器编号. */
    private String serverId;
    /** IDC 服务商. */
    private String idcProvider;

    /** 月成本 CNY. */
    private BigDecimal costMonthly;
    /** 账单日. */
    private Integer billingCycleDay;

    /** 到期时间. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;
}
