package com.nook.biz.node.framework.xray.inbound.snapshot;

import lombok.Builder;

/**
 * Xray inbound 加 user 的入参; gRPC AddUserOperation 与 xray.json clients[] 渲染共用.
 *
 * @author nook
 */
@Builder
public record InboundUserSpec(
        /** 远端 inbound tag */
        String externalInboundRef,
        /** client email; 同 server 全局唯一 */
        String email,
        /** 协议级凭据 (vmess/vless 走 UUID, trojan 走密码) */
        String uuid,
        /** 协议名 (vmess / vless / trojan) */
        String protocol,
        /** vless flow (xtls-rprx-vision 等); 其它协议留空 */
        String flow,
        /** 流量上限字节数; 0 表无限制 */
        long totalBytes,
        /** 到期时间 epoch millis; 0 表永不到期 */
        long expiryEpochMillis,
        /** 同账号 IP 数限制; 0 表无限制 */
        int limitIp
) {
}
