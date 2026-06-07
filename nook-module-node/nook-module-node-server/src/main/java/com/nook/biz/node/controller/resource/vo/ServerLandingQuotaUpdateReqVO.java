package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点配额上限 Update Request VO
 *
 * <p>仅允许 admin 改 3 个配额上限; 运行统计由 agent / 状态机维护.
 *
 * @author nook
 */
@Data
public class ServerLandingQuotaUpdateReqVO {

    /** 出站带宽上限 Mbps; 0=不限. */
    @Min(value = 0)
    private Integer bandwidthMbps;

    /** 总流量配额 GB; null/0=不限. */
    @Min(value = 0)
    private Integer totalGb;

    /** 重置策略 {@link ResourceServerQuotaResetPolicyEnum} */
    @Size(max = 32)
    private String resetPolicy;

    /** 按月流量重置日 1-28; 按月必填, 固定不重置时忽略. */
    @Min(value = 1)
    @Max(value = 28)
    private Integer resetDay;
}
