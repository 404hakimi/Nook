package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.trade.api.enums.TradeTrafficGrantStatusEnum;
import com.nook.biz.trade.api.enums.TradeTrafficGrantTypeEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流量授予 DO; 一笔流量额度, 有类型 + 到期(基础/加购/赠送/补偿)
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_traffic_grant")
public class TradeTrafficGrantDO extends BaseEntity {

    /** 所属订阅. */
    private String subscriptionId;

    /** 类型 {@link TradeTrafficGrantTypeEnum} */
    private String grantType;

    /** 本笔额度(字节). */
    private Long quotaBytes;

    /** 本笔已消耗(字节). */
    private Long usedBytes;

    /** 发放时间. */
    private LocalDateTime grantedAt;

    /** 到期时间. */
    private LocalDateTime expiresAt;

    /** 状态 {@link TradeTrafficGrantStatusEnum} */
    private String status;

    /** 关联订单; 审计用, 可空. */
    private String sourceRef;
}
