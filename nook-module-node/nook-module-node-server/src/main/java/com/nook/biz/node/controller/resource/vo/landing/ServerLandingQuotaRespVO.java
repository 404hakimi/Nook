package com.nook.biz.node.controller.resource.vo.landing;

import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点配额监控 Response VO
 *
 * @author nook
 */
@Data
public class ServerLandingQuotaRespVO {

    /** 落地节点编号. */
    private String serverId;

    /** 出站带宽上限 Mbps; 0=不限. */
    private Integer bandwidthMbps;

    /** 总流量配额 GB; null/0=不限. */
    private Integer totalGb;

    /** 当周期机器已用字节 = rx + tx. */
    private Long usedBytes;

    /** 当周期入站字节. */
    private Long rxBytes;

    /** 当周期出站字节. */
    private Long txBytes;

    /** 重置策略 {@link ResourceServerQuotaResetPolicyEnum} */
    private String resetPolicy;

    /** 按月流量重置日 1-28; 固定不重置时为空. */
    private Integer resetDay;

    /** 限流状态 {@link ResourceServerThrottleStateEnum} */
    private String throttleState;
}
