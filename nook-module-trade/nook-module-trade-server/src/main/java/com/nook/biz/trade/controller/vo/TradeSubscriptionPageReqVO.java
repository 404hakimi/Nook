package com.nook.biz.trade.controller.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订阅分页查询入参.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeSubscriptionPageReqVO extends PageParam {

    private String memberUserId;

    private String planId;

    /** ACTIVE / EXPIRED / CANCELLED. */
    private String status;
}
