package com.nook.biz.node.framework.xray.inbound.protocol;

import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;

/**
 * 协议产出: 协议形态 + 语义参数 + 渲染好的 inbound JSON + 域名/CF token (vmess-tls 路径有值, 其余为空)
 *
 * @author nook
 */
public record InboundProvision(XrayInboundProtocolEnum protocol, InboundParams params, String inboundJson,
                               String fullDomain, String cfApiToken) {
}
