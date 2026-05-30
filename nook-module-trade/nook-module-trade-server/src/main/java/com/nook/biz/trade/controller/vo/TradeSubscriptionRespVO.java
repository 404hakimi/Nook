package com.nook.biz.trade.controller.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 订阅 Response VO
 *
 * @author nook
 */
@Data
public class TradeSubscriptionRespVO {

    /** 主键ID. */
    private String id;

    /** 所属会员. */
    private String memberUserId;

    /** 会员邮箱. */
    private String memberEmail;

    /** 所购套餐. */
    private String planId;

    /** 套餐名. */
    private String planName;

    /** 关联 Xray 客户端. */
    private String xrayClientId;

    /** 生效时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /** 到期时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    /** 订阅状态 {@link TradeSubscriptionStatusEnum} */
    private String status;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
