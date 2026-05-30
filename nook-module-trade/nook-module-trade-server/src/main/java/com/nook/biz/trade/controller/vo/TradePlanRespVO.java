package com.nook.biz.trade.controller.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - 套餐 Response VO
 *
 * @author nook
 */
@Data
public class TradePlanRespVO {

    /** 主键ID. */
    private String id;
    /** 套餐码. */
    private String code;
    /** 套餐名. */
    private String name;
    /** 区域码 (从绑定落地机派生; 未绑落地机时 null). */
    private String regionCode;
    /** IP 类型 (从绑定落地机派生; 未绑落地机时 null). */
    private String ipTypeId;
    /** 月流量配额 (GB). */
    private Integer trafficGb;
    /** 带宽 (Mbps). */
    private Integer bandwidthMbps;
    /** 周期天数. */
    private Integer periodDays;
    /** 售价. */
    private BigDecimal price;
    /** 上下架: 1=上架 0=下架. */
    private Integer enabled;
    /** 备注. */
    private String remark;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** SKU 池容量: LIVE 落地机总数. */
    private Integer capacityTotal;

    /** 剩余可售 (LIVE + AVAILABLE). */
    private Integer capacityAvailable;

    /** 已售 (LIVE + OCCUPIED). */
    private Integer capacityOccupied;
}
