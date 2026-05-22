package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/** Server 运行时状态契约: agent 心跳上报 + admin 拉运行时拼接列表. */
public interface ResourceServerRuntimeApi {

    /**
     * 取单 server 运行时行.
     *
     * @param serverId server 主键
     * @return 运行时 DTO; 装机流程未跑完时 null
     */
    ResourceServerRuntimeRespDTO getByServerId(String serverId);

    /**
     * 批量取运行时 (admin agent 列表用); 缺失 server 不在 map 里.
     *
     * @param serverIds server 主键集
     * @return key=serverId 的 map
     */
    Map<String, ResourceServerRuntimeRespDTO> listByServerIds(Collection<String> serverIds);

    /**
     * 心跳: UPSERT runtime, 更新 last_heartbeat_at + agent_version + last_agent_seen_ip + 清 consecutive_miss / temp_unhealthy.
     *
     * @param serverId     server 主键
     * @param at           心跳时刻
     * @param agentVersion agent 自报版本; 可空表示不更新
     * @param clientIp     HTTP 直连 IP
     * @return 受影响行数; 0 表示装机时 runtime 行还没建, 调用方需告警
     */
    int onHeartbeat(String serverId, LocalDateTime at, String agentVersion, String clientIp);
}
