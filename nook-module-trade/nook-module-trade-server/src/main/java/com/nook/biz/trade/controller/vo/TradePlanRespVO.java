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
    private String regionCode;
    private String ipTypeId;
    private Integer trafficGb;
    private Integer bandwidthMbps;
    private Integer periodDays;
    private Integer limitIp;
    private BigDecimal priceCny;
    private BigDecimal costBasisCny;
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
