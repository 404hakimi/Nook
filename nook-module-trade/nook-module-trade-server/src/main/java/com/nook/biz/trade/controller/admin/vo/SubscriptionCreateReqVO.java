package com.nook.biz.trade.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理后台 - 订阅代客下单 Request VO
 *
 * @author nook
 */
@Data
public class SubscriptionCreateReqVO {

    @NotBlank(message = "会员必填")
    private String memberUserId;

    @NotBlank(message = "套餐必填")
    private String planId;
}
