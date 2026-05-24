package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IP 池账面 DO (1:1, 纯财务记录; 实际带宽/流量配额在 capacity 子表)
 *
 * @author nook
 */
@Data
@TableName("resource_ip_pool_billing")
public class ResourceIpPoolBillingDO {

    @TableId
    private String ipId;

    private BigDecimal costMonthlyUsd;

    /** 账单日 1-28. */
    private Integer billingCycleDay;

    /** IP 到期日. */
    private LocalDate expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
