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

    /** 业务限定带宽 Mbps; 0=不限. agent 跑 tc qdisc 真实 enforce. */
    @Min(value = 0)
    @Max(value = 100000)
    private Integer bandwidthLimitMbps;

    /** 单 server 客户端数硬上限; 0=不限. allocator 候选过滤 + xray inbound 客户数闸. */
    @Min(value = 0)
    @Max(value = 100000)
    private Integer clientMaxCount;

    /** 周期重置策略 {@link ResourceServerQuotaResetPolicyEnum}; 后续做"重置流量"业务时按该策略派计算. */
    @Size(max = 32)
    private String quotaResetPolicy;
}
