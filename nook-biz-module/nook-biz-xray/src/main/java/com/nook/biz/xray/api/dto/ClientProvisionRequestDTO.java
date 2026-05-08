package com.nook.biz.xray.api.dto;

import lombok.Builder;

/** 跨模块开通请求；字段语义参考 controller VO 同名字段。 */
@Builder
public record ClientProvisionRequestDTO(
        String serverId,
        String ipId,
        String memberUserId,
        String externalInboundRef,
        String protocol,
        String transport,
        String listenIp,
        Integer listenPort,
        Long totalBytes,
        Long expiryEpochMillis,
        Integer limitIp,
        String flow
) {
}
