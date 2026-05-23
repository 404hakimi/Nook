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

    private String serverId;
    private String idcProvider;

    /** 承诺带宽 Mbps (账面, 不 enforce). */
    private Integer bandwidthMbps;

    private BigDecimal costMonthlyUsd;
    private Integer billingCycleDay;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiresAt;
}
