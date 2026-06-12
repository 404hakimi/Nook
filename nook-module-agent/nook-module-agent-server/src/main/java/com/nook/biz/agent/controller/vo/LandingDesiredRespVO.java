package com.nook.biz.agent.controller.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 落地机期望配置 Response VO
 *
 * @author nook
 */
@Data
@Builder
public class LandingDesiredRespVO {

    /** 出口期望限速 Mbps; 0 = 不限 (无客户占用). */
    private Integer bandwidthMbps;

    /** socks5 端口 (agent 建 nft 业务流量计数器用); 0 = 未配置 socks5. */
    private Integer socks5Port;
}
