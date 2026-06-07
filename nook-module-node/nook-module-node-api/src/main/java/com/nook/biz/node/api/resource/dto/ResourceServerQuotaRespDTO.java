package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 服务器额度 + 当周期测量 RespDTO; 配额配置与当周期统计现拼给跨模块消费(分配/计量)
 *
 * @author nook
 */
@Data
public class ResourceServerQuotaRespDTO {

    /** server 主键. */
    private String serverId;

    /** 总流量配额 GB; 0/null = 不限. */
    private Integer totalGb;

    /** 出站带宽上限 Mbps; 0=不限. */
    private Integer bandwidthMbps;

    /** 当周期入站字节. */
    private Long rxBytes;

    /** 当周期出站字节. */
    private Long txBytes;

    /** 当周期机器已用字节 = rx + tx. */
    private Long usedBytes;

    /** 用户上行最新累计字节 (给 trade 差分; null 表示未上报). */
    private Long counterUpBytes;

    /** 用户下行最新累计字节 (给 trade 差分; null 表示未上报). */
    private Long counterDownBytes;

    /** 限流状态: 正常 / 已触发限流. */
    private String throttleState;
}
