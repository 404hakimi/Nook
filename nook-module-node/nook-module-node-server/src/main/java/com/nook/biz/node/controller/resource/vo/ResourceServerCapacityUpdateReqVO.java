package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 服务器容量阈值 Update Request VO
 *
 * <p>仅允许 admin 改 4 个业务阈值; rxBytes/txBytes/usedTrafficBytes/throttleState 由 agent / 状态机维护.
 *
 * @author nook
 */
@Data
public class ResourceServerCapacityUpdateReqVO {

    /** 业务月流量阈值 GB; 0/null=不限. 同时是 throttle_state 90% 触发的基数. */
    @Min(value = 0)
    @Max(value = 1000000)
    private Integer monthlyTrafficGb;

    /** 线路机出站带宽容量 Mbps; 供套餐分配不超卖(预留~10%), 0/空=不参与分配; 线路机不做 tc 整形. */
    @Min(value = 0)
    @Max(value = 100000)
    private Integer bandwidthLimitMbps;

    /** 周期重置策略 {@link ResourceServerQuotaResetPolicyEnum}. */
    @Size(max = 32)
    private String quotaResetPolicy;

    /** 按月流量重置日 1-28; MONTHLY 必填, FIXED 忽略. */
    @Min(value = 1)
    @Max(value = 28)
    private Integer resetDay;
}
