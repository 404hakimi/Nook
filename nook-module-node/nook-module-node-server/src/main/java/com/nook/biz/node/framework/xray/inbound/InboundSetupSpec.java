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

    /** 共享 inbound 端口. */
    private Integer sharedInboundPort;

    /** 协议特定入站参数 (多态, 按 protocol 绑定 VmessWsInput / VlessRealityInput); 协议策略各自 downcast 取值. */
    private InboundProtocolInput params;
}
