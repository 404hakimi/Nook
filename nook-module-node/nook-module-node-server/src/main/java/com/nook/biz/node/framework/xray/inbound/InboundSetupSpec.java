package com.nook.biz.node.framework.xray.inbound;

import lombok.Builder;
import lombok.Data;

/**
 * 入站配置规格; framework 中立的装机入站入参. controller 的 XrayInboundConfigVO 在 service 边界映射成它,
 * 协议策略 (resolve / validate / provision / clientFacingDiff) 据此工作, 不再认识 controller VO.
 *
 * @author nook
 */
@Data
@Builder
public class InboundSetupSpec {

    /** 协议; vmess / vless (用于选协议实现). */
    private String protocol;

    /** 监听 IP. */
    private String listenIp;

    /** 共享 inbound 端口. */
    private Integer sharedInboundPort;

    /** WebSocket 接入路径 (vmess). */
    private String wsPath;

    /** REALITY 偷取目标主机名 (vless). */
    private String realityDest;

    /** 绑定根域 system_domain.id (vmess-tls; 空走纯 ws). */
    private String domainId;

    /** 二级域名标签 (vmess 绑域名时). */
    private String subdomain;
}
