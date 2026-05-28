package com.nook.biz.trade.controller.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 套餐 创建/更新 入参.
 *
 * <p>更新时仅 name / bandwidthMbps / costBasisCny / remark 生效; 其余为已售卖不可变字段 (service 忽略).
 *
 * @author nook
 */
@Data
public class TradePlanSaveReqVO {

    /** 更新时必填; 创建留空. */
    private String id;

    @NotBlank(message = "套餐码必填")
    private String code;

    @NotBlank(message = "套餐名必填")
    private String name;

    @NotBlank(message = "区域必填")
    private String regionCode;

    private String ipTypeId;

    @NotNull(message = "流量配额必填")
    @Min(value = 1, message = "流量配额至少 1GB")
    private Integer trafficGb;

    private Integer bandwidthMbps;

    @NotNull(message = "周期天数必填")
    @Min(value = 1, message = "周期至少 1 天")
    private Integer periodDays;

    @Min(value = 0, message = "limitIp 不能为负")
    private Integer limitIp;

    @NotNull(message = "价格必填")
    private BigDecimal priceCny;

    private BigDecimal costBasisCny;

    private String remark;
}
