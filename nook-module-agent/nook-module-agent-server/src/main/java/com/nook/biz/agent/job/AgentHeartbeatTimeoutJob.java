package com.nook.biz.agent.job;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerRuntimeApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 心跳监控 / 告警 Job (仅在状态变化时打日志, 不每分钟刷屏; 真故障的存量迁移由 FrontlineFailoverJob 负责)
 *
 * @author nook
 */
@Slf4j
@Component
public class AgentHeartbeatTimeoutJob {

    @Resource
    private ResourceServerApi resourceServerApi;
    @Resource
    private ResourceServerRuntimeApi resourceServerRuntimeApi;

    /** 上轮各 server 在线状态; 只在状态变化时打一条, 避免持续不健康每分钟刷屏 (内存态, 进程级). */
    private final Map<String, AgentOnlineState> lastState = new HashMap<>();

    @Scheduled(cron = "${nook.agent.heartbeat-timeout-cron:30 * * * * ?}")
    public void check() {
        List<ResourceServerRespDTO> servers = resourceServerApi.listLive();
        if (CollUtil.isEmpty(servers)) {
            lastState.clear();
            return;
        }
        Set<String> ids = CollectionUtils.convertSet(servers, ResourceServerRespDTO::getId);
        Map<String, ResourceServerRuntimeRespDTO> rtMap = resourceServerRuntimeApi.listByServerIds(ids);
        LocalDateTime now = LocalDateTime.now();
        Set<String> liveIds = new HashSet<>(servers.size());
        for (ResourceServerRespDTO srv : servers) {
            liveIds.add(srv.getId());
            ResourceServerRuntimeRespDTO rt = rtMap.get(srv.getId());
            Long elapsedSec = (rt == null || rt.getLastHeartbeatAt() == null) ? null
                    : Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
            AgentOnlineState state = AgentOnlineState.classify(elapsedSec);
            AgentOnlineState prev = lastState.put(srv.getId(), state);
            // 只对"告警态"(不稳/掉线/从未心跳)打日志; WARN(1-3min) 是正常抖动 (心跳本就 60s 一次), 不打, 免健康机刷屏
            boolean alerting = state == AgentOnlineState.TEMP_UNHEALTHY
                    || state == AgentOnlineState.OFFLINE || state == AgentOnlineState.NEVER;
            boolean wasAlerting = prev == AgentOnlineState.TEMP_UNHEALTHY
                    || prev == AgentOnlineState.OFFLINE || prev == AgentOnlineState.NEVER;
            if (alerting && state != prev) {
                switch (state) {
                    case OFFLINE -> log.error("[heartbeat] {} 掉线 (≥5min, elapsed={}s); 存量迁移交 FrontlineFailoverJob",
                            srv.getName(), elapsedSec);
                    case TEMP_UNHEALTHY -> log.warn("[heartbeat] {} 心跳不稳 (3-5min, elapsed={}s); allocator 已暂停分配新订阅",
                            srv.getName(), elapsedSec);
                    case NEVER -> log.warn("[heartbeat] {} 从未收到心跳 (装机中 / runtime 行缺失?)", srv.getName());
                    default -> { }
                }
            } else if (wasAlerting && !alerting) {
                log.info("[heartbeat] {} 心跳恢复正常", srv.getName());
            }
        }
        // 退役 / 删除的 server 清掉残留态, 防 map 无限增长
        lastState.keySet().retainAll(liveIds);
    }
}
