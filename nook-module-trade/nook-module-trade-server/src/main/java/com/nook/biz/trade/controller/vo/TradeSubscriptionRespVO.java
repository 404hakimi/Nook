package com.nook.biz.trade.controller.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅 Response (含套餐名 enrich).
 *
 * @author nook
 */
@Data
public class TradeSubscriptionRespVO {

    private String id;
    private String memberUserId;
    private String planId;

    /** enrich: 套餐名. */
    private String planName;

    private String xrayClientId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    /** ACTIVE / EXPIRED / CANCELLED. */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
