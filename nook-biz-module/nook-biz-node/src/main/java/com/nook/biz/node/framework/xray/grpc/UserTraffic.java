package com.nook.biz.node.framework.xray.grpc;

/** Xray stats 读用户流量结果; 字节计数, 0 表无限制 (totalBytes/expiry/enabled 在 gRPC 模式下由 nook 业务侧维护). */
public record UserTraffic(
        String email,
        long upBytes,
        long downBytes,
        long totalBytes,
        long expiryEpochMillis,
        boolean enabled
) {
}
