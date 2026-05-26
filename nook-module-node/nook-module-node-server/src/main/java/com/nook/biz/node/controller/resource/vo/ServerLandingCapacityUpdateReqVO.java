package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点容量配置更新 Request VO
 *
 * <p>仅允许 admin 改 3 个业务阈值; rxBytes/txBytes/usedTrafficBytes/throttleState 由 agent / 状态机维护.
 *
 * @author nook
 */
@Data
public class ServerLandingCapacityUpdateReqVO {

    /** dante 实际限速 Mbps; 0=不限. */
    @Min(value = 0)
    private Integer bandwidthLimitMbps;

    /** 月流量上限 GB; null/0=不限. */
    @Min(value = 0)
    private Integer monthlyTrafficGb;

    /** 周期重置策略 {@link ResourceServerQuotaResetPolicyEnum} */
    @Size(max = 32)
    private String quotaResetPolicy;
}
