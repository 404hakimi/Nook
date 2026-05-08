package com.nook.biz.xray.api.dto;

public record InboundTrafficDTO(
        String email,
        long upBytes,
        long downBytes,
        long totalBytes,
        long expiryEpochMillis,
        boolean enabled
) {
}
