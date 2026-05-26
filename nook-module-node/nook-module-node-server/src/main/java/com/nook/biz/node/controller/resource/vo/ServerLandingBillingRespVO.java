package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 管理后台 - SOCKS5 落地节点账面 Response VO
 *
 * @author nook
 */
@Data
public class ServerLandingBillingRespVO {

    /** 落地节点编号 (== resource_server.id). */
    private String serverId;

    /** 月度成本 USD. */
    private BigDecimal costMonthlyUsd;

    /** 账单日 1-28. */
    private Integer billingCycleDay;

    /** IP 到期日. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;
}
