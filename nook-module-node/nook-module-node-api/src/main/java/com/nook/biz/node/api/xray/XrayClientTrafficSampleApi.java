package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.AgentStatSnapshotDTO;
import com.nook.biz.node.api.xray.dto.SampleStatDTO;

import java.util.Map;

/** Agent → backend xray user 流量上报契约. */
public interface XrayClientTrafficSampleApi {

    /**
     * 把 agent 上报的 xray statsquery 快照应用到 xray_client_traffic_sample 表 (按 email 上车).
     *
     * @param serverId server 主键 (来源 agent 鉴权)
     * @param snapshot key=email, value=up/down bytes 累计快照
     * @return 入库统计 (upserted = 命中已有 client 写入条数, skipped = 孤儿 email 跳过条数)
     */
    SampleStatDTO applyAgentStats(String serverId, Map<String, AgentStatSnapshotDTO> snapshot);
}
