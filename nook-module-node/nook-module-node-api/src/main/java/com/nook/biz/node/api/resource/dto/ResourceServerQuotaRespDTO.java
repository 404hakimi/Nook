package com.nook.biz.node.api.resource.dto;

import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import lombok.Data;

/**
 * 服务器额度 + 当周期测量 Response DTO
 *
 * @author nook
 */
@Data
public class ResourceServerQuotaRespDTO {

    /** 服务器ID. */
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

    /** 用户上行最新累计字节; null = 未上报. */
    private Long counterUpBytes;

    /** 用户下行最新累计字节; null = 未上报. */
    private Long counterDownBytes;

    /** 限流状态 {@link ResourceServerThrottleStateEnum} */
    private String throttleState;
}
