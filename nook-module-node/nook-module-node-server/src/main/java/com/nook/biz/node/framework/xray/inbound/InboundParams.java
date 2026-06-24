package com.nook.biz.node.framework.xray.inbound;

/**
 * Xray inbound 语义参数基类; 各协议参数子类的多态根, 按 protocol_key 还原到对应子类 (见 InboundParamsResolver)。
 *
 * <p>admin 详情投影改由各协议 {@link InboundProtocol#formPrefill} 输出; 基类不再预置 vmess 形状的 getter。
 *
 * @author nook
 */
public abstract class InboundParams {
}
