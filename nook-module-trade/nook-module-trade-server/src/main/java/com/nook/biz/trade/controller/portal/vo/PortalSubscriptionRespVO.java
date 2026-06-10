package com.nook.biz.trade.controller.portal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端 - 会员订阅 Response VO
 *
 * @author nook
 */
@Data
public class PortalSubscriptionRespVO {

    private String subscriptionId;

    /** 套餐名. */
    private String planName;

    /** 套餐月流量 GB. */
    private Integer trafficGb;

    /** 该套餐在 Clash 订阅里的节点组名 (客户端按此展开节点). */
    private String groupName;

    /** 剩余可用额度 (字节). */
    private Long remainingBytes;

    /** 订阅状态. */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}
