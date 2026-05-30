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

    /** 套餐总流量配额, 单位 GB. */
    private Integer trafficGb;

    /** 本周期已用流量, 单位字节. */
    private Long usedBytes;

    /** 所在线路机 server id. */
    private String frontlineServerId;

    /** 所在线路机出网 IP. */
    private String frontlineIp;

    /** 占用的落地机 server id. */
    private String landingServerId;

    /** 占用的落地机出网 IP. */
    private String landingIp;

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
