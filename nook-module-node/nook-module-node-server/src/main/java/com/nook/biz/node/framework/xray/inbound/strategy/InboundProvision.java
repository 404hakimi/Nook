package com.nook.biz.node.framework.xray.inbound.strategy;

import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;

import java.util.Map;

/**
 * 协议策略产出: 协议形态 + 语义参数 + 模板占位符值
 *
 * @author nook
 */
public record InboundProvision(XrayInboundProtocolEnum protocol, InboundParams params, Map<String, Object> templateVars) {
}
