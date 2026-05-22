package com.nook.biz.node.api.xray.dto;

/**
 * Agent 上报的 Xray 用户流量快照 DTO
 *
 * @author nook
 */
public record AgentStatSnapshotDTO(long upBytes, long downBytes) {
}
