package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 服务器账面 DO
 *
 * @author nook
 */
@Data
@TableName("resource_server_billing")
public class ResourceServerBillingDO {

    @TableId
    private String serverId;

    private String idcProvider;

    /** 承诺带宽 Mbps (账面, 不 enforce). */
    private Integer bandwidthMbps;

    private BigDecimal costMonthlyUsd;

    /** 账单日 1-28. */
    private Integer billingCycleDay;

    private LocalDate expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
