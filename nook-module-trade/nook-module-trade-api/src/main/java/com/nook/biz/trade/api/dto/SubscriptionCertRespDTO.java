package com.nook.biz.trade.api.dto;

import lombok.Data;

/**
 * 订阅凭证响应 DTO (跨模块)
 *
 * @author nook
 */
@Data
public class SubscriptionCertRespDTO {

    /** 凭证ID. */
    private String id;

    /** 所属订阅. */
    private String subscriptionId;

    /** 来源: BASE=基础 ADDON=加购. */
    private String source;

    /** 分配的线路机. */
    private String serverId;

    /** 分配的落地机. */
    private String ipId;

    /** 连接身份. */
    private String authUser;

    /** 连接密钥. */
    private String authSecret;

    /** 期望态: ACTIVE/SUSPENDED/REVOKED. */
    private String certStatus;
}
