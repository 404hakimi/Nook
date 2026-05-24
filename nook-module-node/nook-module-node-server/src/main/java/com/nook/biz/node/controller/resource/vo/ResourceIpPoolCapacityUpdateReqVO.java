package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - IP 池容量监控 Update Request VO
 *
 * <p>仅含可由 admin 编辑的字段: 限速 + 月流量上限 + 重置策略.
 * rx/tx/used_traffic_bytes/throttle_state 由 agent 上报 / 状态机改, admin 不直接编辑.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolCapacityUpdateReqVO {

    /** dante 实际限速 Mbps; 0=不限. agent 改 sockd.conf 落实. */
    @NotNull(message = "bandwidthLimitMbps 必填; 0=不限")
    @Min(value = 0, message = "限速不能为负")
    @Max(value = 1_000_000, message = "限速不能超过 1000000 Mbps")
    private Integer bandwidthLimitMbps;

    /** 月流量上限 GB; null/0=不限. */
    @Min(value = 0, message = "流量上限不能为负")
    @Max(value = 10_000_000, message = "流量上限不能超过 10000000 GB")
    private Integer monthlyTrafficGb;

    /** 周期重置策略. */
    @Size(max = 32)
    @Pattern(regexp = "CALENDAR_MONTH|BILLING_CYCLE|FIXED",
            message = "重置策略须为 CALENDAR_MONTH / BILLING_CYCLE / FIXED")
    private String quotaResetPolicy;
}
