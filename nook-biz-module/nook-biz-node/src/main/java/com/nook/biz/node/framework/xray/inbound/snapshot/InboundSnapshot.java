package com.nook.biz.node.framework.xray.inbound.snapshot;

/** 远端 xray.json inbound 段读出的快照; SDK 原始字段 (tag = Xray config 里的 tag, 业务侧映射成 externalInboundRef). */
public record InboundSnapshot(
        String tag,
        String remark,
        String protocol,
        int port,
        boolean enabled,
        int clientCount
) {
}
