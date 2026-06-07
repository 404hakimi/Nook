package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订阅接入点流量计量 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_subscription_traffic")
public class TradeSubscriptionTrafficDO extends BaseEntity {

    /** 接入点(凭证). */
    private String certId;

    /** 所属订阅. */
    private String subscriptionId;

    /** 在量哪台落地机. */
    private String landingServerId;

    /** 周期起点. */
    private LocalDateTime startTime;

    /** 周期终点; 空 = 当前在写那行. */
    private LocalDateTime endTime;

    /** 本周期用户上行用量. */
    private Long upBytes;

    /** 本周期用户下行用量. */
    private Long downBytes;

    /** 本周期已用 = 上 + 下. */
    private Long usedBytes;

    /** 游标: 上次处理到的上行计数器值(对落地机最新累计做差). */
    private Long lastCounterUpBytes;

    /** 游标: 上次处理到的下行计数器值. */
    private Long lastCounterDownBytes;

    /** 最近计量时刻. */
    private LocalDateTime lastSampledAt;
}
