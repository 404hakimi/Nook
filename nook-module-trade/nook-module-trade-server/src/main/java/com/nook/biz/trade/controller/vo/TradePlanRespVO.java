package com.nook.biz.trade.controller.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐 Response (列表/详情; 含 SKU 池容量).
 *
 * @author nook
 */
@Data
public class TradePlanRespVO {

    private String id;
    private String code;
    private String name;
    /** 区域码 (从绑定落地机派生; 未绑落地机时 null). */
    private String regionCode;
    /** IP 类型 (从绑定落地机派生; 未绑落地机时 null). */
    private String ipTypeId;
    private Integer trafficGb;
    private Integer bandwidthMbps;
    private Integer periodDays;
    private BigDecimal price;
    private Integer enabled;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** SKU 池容量: LIVE 落地机总数. */
    private Integer capacityTotal;

    /** 剩余可售 (LIVE + AVAILABLE). */
    private Integer capacityAvailable;

    /** 已售 (LIVE + OCCUPIED). */
    private Integer capacityOccupied;
}
