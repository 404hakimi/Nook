package com.nook.biz.node.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.enums.ResourceServerLifecycleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Agent 心跳超时分级判定: 1-3min WARN, 3-5min temp_unhealthy (allocator 跳过), ≥5min failover. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHeartbeatTimeoutJob {

    private static final long WARN_THRESHOLD_SEC = 60;          // 1min
    private static final long TEMP_UNHEALTHY_THRESHOLD_SEC = 180; // 3min
    private static final long FAILOVER_THRESHOLD_SEC = 300;     // 5min

    private final ResourceServerMapper resourceServerMapper;
    private final ResourceServerRuntimeMapper resourceServerRuntimeMapper;

    @Scheduled(cron = "${nook.agent.heartbeat-timeout-cron:30 * * * * ?}")
    public void check() {
        List<ResourceServerDO> servers = resourceServerMapper.selectList(
                Wrappers.<ResourceServerDO>lambdaQuery()
                        .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                        .eq(ResourceServerDO::getDeleted, 0));
        if (servers == null || servers.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int warn = 0, tempUnhealthy = 0, failover = 0;
        for (ResourceServerDO srv : servers) {
            ResourceServerRuntimeDO rt = resourceServerRuntimeMapper.selectById(srv.getId());
            if (rt == null || rt.getLastHeartbeatAt() == null) {
                log.warn("[check] serverId={} 无 runtime 行或未收到首次心跳", srv.getId());
                continue;
            }
            long elapsed = Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
            if (elapsed >= FAILOVER_THRESHOLD_SEC) {
                failover++;
                log.error("[check] FAILOVER serverId={} elapsed={}s (≥5min); 池内切换待实现, admin 自行介入",
                        srv.getId(), elapsed);
            } else if (elapsed >= TEMP_UNHEALTHY_THRESHOLD_SEC) {
                if (rt.getTempUnhealthy() == null || rt.getTempUnhealthy() == 0) {
                    resourceServerRuntimeMapper.markTempUnhealthy(srv.getId(), 1);
                    tempUnhealthy++;
                    log.warn("[check] TEMP_UNHEALTHY: serverId={} elapsed={}s (3-5min); allocator 跳过此 server",
                            srv.getId(), elapsed);
                }
            } else if (elapsed >= WARN_THRESHOLD_SEC) {
                warn++;
                log.warn("[check] AGENT_OFFLINE WARN: serverId={} elapsed={}s (1-3min, 观察中)",
                        srv.getId(), elapsed);
            }
        }
        if (warn + tempUnhealthy + failover > 0) {
            log.info("[check] 扫描完成: live={} warn={} tempUnhealthy={} failover={}",
                    servers.size(), warn, tempUnhealthy, failover);
        }
    }
}
