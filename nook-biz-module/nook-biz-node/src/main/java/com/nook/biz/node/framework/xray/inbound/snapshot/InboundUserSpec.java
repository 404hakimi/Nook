package com.nook.biz.node.framework.xray.inbound.snapshot;

import lombok.Builder;

/** Xray inbound 加 user 的入参; gRPC AddUserOperation 与 xray.json clients[] 渲染共用. */
@Builder
public record InboundUserSpec(
        String externalInboundRef,
        String email,
        String uuid,
        String protocol,
        String flow,
        long totalBytes,
        long expiryEpochMillis,
        int limitIp
) {
}
