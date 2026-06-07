package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - 服务器配额 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerQuotaRespVO {

    /** 服务器编号. */
    private String serverId;

    /** 总流量配额 GB; 0/null = 不限. */
    private Integer totalGb;

    /** 出站带宽上限 Mbps; 0/空=不参与分配, 不整形. */
    private Integer bandwidthMbps;

    /** 当周期入站字节. */
    private Long rxBytes;

    /** 当周期出站字节. */
    private Long txBytes;

    /** 当周期机器已用字节 = rx + tx. */
    private Long usedBytes;

    /** 重置策略: 按月 / 固定不重置. */
    private String resetPolicy;

    /** 按月流量重置日 1-28; 固定不重置时为空. */
    private Integer resetDay;

    /** 限流状态: 正常 / 已触发限流. */
    private String throttleState;
}
