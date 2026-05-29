package com.nook.biz.node.framework.xray.inbound.snapshot;

import lombok.Builder;
import lombok.Data;

/**
 * Xray inbound 加 user 的入参; xray.json clients[] 渲染共用.
 *
 * @author nook
 */
@Data
@Builder
public class InboundUserSpec {

    /** client email; 同 server 全局唯一. */
    private String email;

    /** 协议级凭据 (vmess/vless 走 UUID, trojan 走密码). */
    private String uuid;

    /** 协议名 (vmess / vless / trojan). */
    private String protocol;

    /** vless flow (xtls-rprx-vision 等); 其它协议留空. */
    private String flow;
}
