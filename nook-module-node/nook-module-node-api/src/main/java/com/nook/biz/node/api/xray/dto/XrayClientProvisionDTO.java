package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/**
 * Xray 客户端开通入参 (跨模块契约; trade 下单时调).
 *
 * @author nook
 */
@Data
public class XrayClientProvisionDTO {

    /** 线路机 server id. */
    private String serverId;

    /** 落地机 server id. */
    private String ipId;

    private String memberUserId;

    /** 流量上限 (字节); 0 = 不限. */
    private Long totalBytes;

    /** 到期时间戳 (毫秒); 0 = 永久. */
    private Long expiryEpochMillis;

    /** 同时连接 IP 数; 0 = 不限. */
    private Integer limitIp;

    /** 带宽上限 Mbps; 0 = 不限 (落地机 tc 按此限速). */
    private Integer bandwidthMbps;
}
