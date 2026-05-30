package com.nook.biz.trade.controller.vo;

import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 换机历史分页查询 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeSubscriptionChangeLogPageReqVO extends PageParam {

    /** 订阅 id. */
    private String subscriptionId;

    /** 会员 id. */
    private String memberUserId;

    /** 换机类型 {@link TradeSubscriptionChangeTypeEnum} */
    private String changeType;

    /** 变更原因 {@link TradeSubscriptionChangeReasonEnum} */
    private String reason;
}
