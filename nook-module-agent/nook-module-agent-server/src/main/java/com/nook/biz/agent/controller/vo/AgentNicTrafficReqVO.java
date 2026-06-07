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

    /** 网卡入站累计字节. */
    @NotNull(message = "rxBytes 不能为空")
    @PositiveOrZero
    private Long rxBytes;

    /** 网卡出站累计字节. */
    @NotNull(message = "txBytes 不能为空")
    @PositiveOrZero
    private Long txBytes;

    /** socks5 用户上行累计字节 (落地机 nft 计数器读出); 未上报为 null. */
    @PositiveOrZero
    private Long bizUpBytes;

    /** socks5 用户下行累计字节 (落地机 nft 计数器读出); 未上报为 null. */
    @PositiveOrZero
    private Long bizDownBytes;
}
