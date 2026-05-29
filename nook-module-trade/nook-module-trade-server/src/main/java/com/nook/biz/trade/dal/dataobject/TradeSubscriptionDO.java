package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
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

    /** 所属会员. */
    private String memberUserId;

    /** 所购套餐. */
    private String planId;

    /** 绑定的 xray 客户端, 1:1. */
    private String xrayClientId;

    /** 生效时间. */
    private LocalDateTime startedAt;

    /** 到期时间; 由 startedAt + 套餐 periodDays 推得. */
    private LocalDateTime expiresAt;

    /** 订阅状态 {@link TradeSubscriptionStatusEnum} */
    private String status;
}
