package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订阅 DO (1 sub = 1 套餐 = 1 xray_client)
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_subscription")
public class TradeSubscriptionDO extends BaseEntity {

    private String memberUserId;

    /** FK → trade_plan.id. */
    private String planId;

    /** 1:1 → xray_client.id. */
    private String xrayClientId;

    private LocalDateTime startedAt;

    private LocalDateTime expiresAt;

    /** ACTIVE / EXPIRED / CANCELLED; {@link com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum}. */
    private String status;
}
