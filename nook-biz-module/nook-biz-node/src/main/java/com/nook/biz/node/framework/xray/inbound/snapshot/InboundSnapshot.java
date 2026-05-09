package com.nook.biz.node.framework.xray.inbound.snapshot;

/**
 * 远端 xray.json inbound 段读出的快照.
 *
 * @author nook
 */
public record InboundSnapshot(
        /** Xray config 里的 tag, 业务侧映射成 externalInboundRef */
        String tag,
        /** 备注名 (config.remark, 缺省回 tag) */
        String remark,
        /** 协议 (vmess / vless / trojan ...) */
        String protocol,
        /** 监听端口 */
        int port,
        /** 是否启用; nook 标配 config 不写该字段, 默认 true */
        boolean enabled,
        /** settings.clients[] 的长度; 非 client-based inbound 为 0 */
        int clientCount
) {
}
