package com.nook.biz.agent.controller.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * Agent NIC 流量上报 Request VO
 *
 * @author nook
 */
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

    /** socks5 业务流量累计字节 (落地机 nft 计数器读出, 双向之和); 老 agent 不上报为 null, 后端回退整机 tx 计量. */
    @PositiveOrZero
    private Long bizUsedBytes;
}
