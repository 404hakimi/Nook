package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Xray 入站协议形态; key 对应 xray_inbound_protocol.protocol_key 与模板
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum XrayInboundProtocolEnum {

    VMESS_WS_TLS("vmess-ws-tls", "vmess", "ws", "tls"),
    VMESS_WS_PLAIN("vmess-ws-plain", "vmess", "ws", "none"),
    VLESS_REALITY("vless-reality", "vless", "tcp", "reality"),
    ;

    /** 协议形态标识, 对应 xray_inbound_protocol.protocol_key. */
    private final String key;

    /** xray inbound.protocol. */
    private final String protocol;

    /** xray streamSettings.network. */
    private final String transport;

    /** 安全层取值: none / tls / reality. */
    private final String security;

    public static XrayInboundProtocolEnum fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (XrayInboundProtocolEnum e : values()) {
            if (e.key.equalsIgnoreCase(key)) {
                return e;
            }
        }
        return null;
    }
}
