package com.nook.biz.xray.backend.dto;

/**
 * 单个 client 的累计流量与配额状态。
 * 字节单位；0 表示无限制 (符合 3x-ui 与 Xray 协议常见约定)。
 *   upBytes / downBytes  累计上行/下行字节
 *   totalBytes           流量上限(配额)；0=不限
 *   expiryEpochMillis    到期时间戳；0=不限期
 *   enabled              是否启用 (3xui 字段对应 enable, gRPC 模式我们当作 true)
 */
public record XrayClientTraffic(
        String email,
        long upBytes,
        long downBytes,
        long totalBytes,
        long expiryEpochMillis,
        boolean enabled
) {
}
