package com.nook.biz.node.framework.xray.inbound;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nook.biz.node.framework.xray.inbound.vless.VlessRealityInput;
import com.nook.biz.node.framework.xray.inbound.vmess.VmessWsInput;

/**
 * 协议特定入站参数的多态基类; 共享输入层 (XrayInboundConfigVO / InboundSetupSpec) 只持本类型, 不再平铺各协议字段。
 *
 * <p>反序列化按兄弟字段 {@code protocol} (EXTERNAL_PROPERTY) 绑定到对应子类型, 各协议子类自带 Bean Validation。
 * 加协议 = 加一个子类型 + 在下面 {@code @JsonSubTypes} 注册一行 + 一个 {@link InboundProtocol} 实现 + 枚举形态,
 * 共享 VO / Spec / 映射零改。
 *
 * @author nook
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "protocol")
@JsonSubTypes({
        @JsonSubTypes.Type(value = VmessWsInput.class, name = "vmess"),
        @JsonSubTypes.Type(value = VlessRealityInput.class, name = "vless"),
})
public abstract class InboundProtocolInput {
}
