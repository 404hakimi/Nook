package com.nook.biz.agent.controller.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Agent 上报机房 NIC 流量 (从 vnstat 取). */
@Data
public class AgentNicTrafficReqVO {

    /** 周期内累计入站字节. */
    @NotNull(message = "rxBytes 不能为空")
    @PositiveOrZero
    private Long rxBytes;

    /** 周期内累计出站字节. */
    @NotNull(message = "txBytes 不能为空")
    @PositiveOrZero
    private Long txBytes;

    /** 周期起点 yyyy-MM-dd. Agent 端按 server.capacity.quota_reset_policy 算出. */
    private String periodStart;
}
