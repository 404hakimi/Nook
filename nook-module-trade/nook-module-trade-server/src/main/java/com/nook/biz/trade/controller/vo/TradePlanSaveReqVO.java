package com.nook.biz.trade.controller.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台 - 套餐保存 Request VO
 *
 * @author nook
 */
@Data
public class TradePlanSaveReqVO {

    /** 主键ID; 更新时必填, 创建留空. */
    private String id;

    /** 套餐码. */
    @NotBlank(message = "套餐码必填")
    private String code;

    /** 套餐名. */
    @NotBlank(message = "套餐名必填")
    private String name;

    /** 区域码. */
    @NotBlank(message = "区域必填")
    private String regionCode;

    /** IP 类型. */
    @NotBlank(message = "IP 类型必填")
    private String ipTypeId;

    /** 月流量配额 (GB). */
    @NotNull(message = "流量配额必填")
    @Min(value = 1, message = "流量配额至少 1GB")
    private Integer trafficGb;

    /** 带宽 (Mbps). */
    @NotNull(message = "带宽必填")
    @Min(value = 1, message = "带宽至少 1Mbps")
    private Integer bandwidthMbps;

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
