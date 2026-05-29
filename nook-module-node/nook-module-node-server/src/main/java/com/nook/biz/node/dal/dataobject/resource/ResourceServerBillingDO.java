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

    /** 服务器 id (主键). */
    @TableId
    private String serverId;

    /** 云厂商/机房. */
    private String idcProvider;

    /** 月成本 CNY. */
    private BigDecimal costMonthly;

    /** 账单日 1-28; NIC 流量按此日重置. */
    private Integer billingCycleDay;

    /** 服务器租约到期日. */
    private LocalDate expiresAt;

    /** 创建时间. */
    private LocalDateTime createdAt;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
