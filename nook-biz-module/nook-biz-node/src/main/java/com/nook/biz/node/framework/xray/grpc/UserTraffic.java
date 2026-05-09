package com.nook.biz.node.framework.xray.grpc;

/**
 * Xray stats 读用户流量结果; 字节计数, 0 表无限制.
 *
 * @author nook
 */
public record UserTraffic(
        /** 用户 email */
        String email,
        /** 累计上行字节数 */
        long upBytes,
        /** 累计下行字节数 */
        long downBytes,
        /** 流量上限字节数; gRPC 模式下由 nook 业务侧维护, 0 表无限制 */
        long totalBytes,
        /** 到期时间 epoch millis; gRPC 模式下由 nook 业务侧维护, 0 表永不到期 */
        long expiryEpochMillis,
        /** 是否启用; gRPC 模式下由 nook 业务侧维护 */
        boolean enabled
) {
}
