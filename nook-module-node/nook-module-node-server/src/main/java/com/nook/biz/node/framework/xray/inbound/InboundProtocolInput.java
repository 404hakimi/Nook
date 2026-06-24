package com.nook.biz.node.framework.xray.inbound;

/**
 * 协议特定入站参数的多态基类; 共享输入层 (XrayInboundConfigVO / InboundSetupSpec) 只持本类型, 不再平铺各协议字段。
 *
 * <p>JSON 反序列化的多态绑定 (按兄弟字段 {@code protocol} 选子类型) 声明在 {@code XrayInboundConfigVO.params}
 * 字段上 ({@code @JsonTypeInfo As.EXTERNAL_PROPERTY}) —— EXTERNAL_PROPERTY 需要外层上下文, 必须放属性而非基类。
 * 加协议 = 加一个子类型 + 在 VO 的 {@code @JsonSubTypes} 注册一行 + 一个 {@link InboundProtocol} 实现 + 枚举形态。
 *
 * @author nook
 */
public abstract class InboundProtocolInput {
}
