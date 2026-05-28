package com.nook.biz.trade.controller.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * admin 代客下单入参.
 *
 * @author nook
 */
@Data
public class AdminCreateSubReqVO {

    @NotBlank(message = "会员必填")
    private String memberUserId;

    @NotBlank(message = "套餐必填")
    private String planId;
}
