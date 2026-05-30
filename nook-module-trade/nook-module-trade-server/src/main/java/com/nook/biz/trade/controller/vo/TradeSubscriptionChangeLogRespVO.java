package com.nook.biz.trade.controller.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 订阅换机历史 Response VO
 *
 * @author nook
 */
@Data
public class TradeSubscriptionChangeLogRespVO {

    /** 主键ID. */
    private String id;

    /** 订阅 id. */
    private String subscriptionId;

    /** 会员 id. */
    private String memberUserId;

    /** 会员邮箱. */
    private String memberEmail;

    /** 换机类型 {@link TradeSubscriptionChangeTypeEnum} */
    private String changeType;

    /** 原机器 server id. */
    private String oldServerId;

    /** 原机器出网 IP. */
    private String oldServerIp;

    /** 新机器 server id. */
    private String newServerId;

    /** 新机器出网 IP. */
    private String newServerIp;

    /** 变更原因 {@link TradeSubscriptionChangeReasonEnum} */
    private String reason;

    /** 操作者 admin id; 系统触发为 system. */
    private String operator;

    /** 变更时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
