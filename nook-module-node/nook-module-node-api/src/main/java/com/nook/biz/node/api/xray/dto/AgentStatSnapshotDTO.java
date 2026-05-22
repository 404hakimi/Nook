package com.nook.biz.node.api.xray.dto;

/** Xray statsquery 单 user 流量快照 (上下行字节累计). */
public record AgentStatSnapshotDTO(long upBytes, long downBytes) {
}
