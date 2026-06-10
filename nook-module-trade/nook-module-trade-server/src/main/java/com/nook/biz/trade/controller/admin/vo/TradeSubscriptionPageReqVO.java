package com.nook.biz.trade.controller.admin.vo;

import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 订阅分页查询 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeSubscriptionPageReqVO extends PageParam {

    /** 所属会员. */
    private String memberUserId;

    /** 所购套餐. */
    private String planId;

    /** 订阅状态 {@link TradeSubscriptionStatusEnum} */
    private String status;
}
