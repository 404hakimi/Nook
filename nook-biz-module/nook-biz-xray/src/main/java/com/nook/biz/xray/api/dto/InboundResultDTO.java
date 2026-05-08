package com.nook.biz.xray.api.dto;

/** 开通结果；inboundEntityId 是 xray_inbound.id，业务侧用它后续吊销/查询。 */
public record InboundResultDTO(
        String inboundEntityId,
        String clientUuid,
        String clientEmail
) {
}
