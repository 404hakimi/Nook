package com.nook.biz.xray.backend.dto;

import lombok.Builder;

/**
 * 加客户端的入参。
 * 字段语义在两种 backend 下尽量对齐：
 *   externalInboundRef  threexui=面板 inbound id, xray-grpc=inbound tag
 *   email/uuid          双 backend 都用，3xui 直接落 panel client，gRPC 写入 user.email/account 字段
 *   protocol/flow       gRPC 必须显式给(决定要塞哪个 Account proto), 3xui 可从面板读
 *   totalBytes/expiry/limitIp 仅 3xui 支持(面板维度), gRPC 模式忽略——流量与到期由 nook 业务侧控制
 */
@Builder
public record XrayClientSpec(
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
