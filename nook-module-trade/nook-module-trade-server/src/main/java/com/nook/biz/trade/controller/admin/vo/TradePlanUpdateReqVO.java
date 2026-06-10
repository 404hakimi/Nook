package com.nook.biz.trade.controller.admin.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台 - 套餐更新 Request VO
 *
 * @author nook
 */
@Data
public class TradePlanUpdateReqVO {

    /** 主键ID. */
    @NotBlank(message = "套餐ID必填")
    private String id;

    /** 套餐名. */
    @NotBlank(message = "套餐名必填")
    private String name;

    /** 周期天数. */
    @NotNull(message = "周期天数必填")
    @Min(value = 1, message = "周期至少 1 天")
    private Integer periodDays;

    /** 售价. */
    @NotNull(message = "价格必填")
    private BigDecimal price;

    /** 备注. */
    private String remark;
}
