package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 服务器配额上限 Update Request VO
 *
 * <p>仅允许 admin 改 4 个配额上限; 运行统计由 agent / 状态机维护.
 *
 * @author nook
 */
@Data
public class ResourceServerQuotaUpdateReqVO {

    /** 总流量配额 GB; 0/null=不限. 建议填机房配额的 ~90% 留余量. */
    @Min(value = 0)
    @Max(value = 1000000)
    private Integer totalGb;

    /** 出站带宽上限 Mbps; 落地机真实限速, 线路机供分配不超卖, 0/空=不参与分配. */
    @Min(value = 0)
    @Max(value = 100000)
    private Integer bandwidthMbps;

    /** 重置策略 {@link ResourceServerQuotaResetPolicyEnum}. */
    @Size(max = 32)
    private String resetPolicy;

    /** 按月流量重置日 1-28; 按月必填, 固定不重置时忽略. */
    @Min(value = 1)
    @Max(value = 28)
    private Integer resetDay;
}
