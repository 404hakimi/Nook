package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.trade.api.enums.TradeQuotaStatusEnum;
import com.nook.biz.trade.api.enums.TradeQuotaTypeEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订阅额度账本 DO; 一笔流量额度, 有类型 + 有效期(基础/加购/赠送/补偿)
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_subscription_quota")
public class TradeSubscriptionQuotaDO extends BaseEntity {

    /** 所属订阅. */
    private String subscriptionId;

    /** 类型 {@link TradeQuotaTypeEnum} */
    private String quotaType;

    /** 本笔配额(字节). */
    private Long totalBytes;

    /** 本笔已扣(字节). */
    private Long usedBytes;

    /** 发放/生效时间. */
    private LocalDateTime startTime;

    /** 到期时间. */
    private LocalDateTime endTime;

    /** 状态 {@link TradeQuotaStatusEnum} */
    private String status;

    /** 关联订单; 审计用, 可空. */
    private String sourceRef;
}
