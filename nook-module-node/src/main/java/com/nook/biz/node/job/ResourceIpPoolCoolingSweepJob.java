package com.nook.biz.node.job;

import com.nook.biz.node.service.resource.ResourceIpPoolService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 冷却到期 IP 扫回 available; 不走 OpOrchestrator (无 SSH 副作用, 纯 DB 状态切换).
 *
 * <p>退订后 IP 进 cooling, cooling_until 到期需有人扫回 available 才能再次分配, 否则 IP 池逐渐枯竭.
 *
 * @author nook
 */
@Slf4j
@Component
public class ResourceIpPoolCoolingSweepJob {

    @Resource
    private ResourceIpPoolService resourceIpPoolService;

    /**
     * 默认 5 分钟一轮; 相对最短冷却 30 分钟足够细, 失败重试到下一轮.
     */
    @Scheduled(cron = "${nook.ip-pool.cooling-sweep-cron:0 */5 * * * ?}")
    public void sweep() {
        try {
            int n = resourceIpPoolService.sweepExpiredCooling();
            if (n > 0) {
                log.info("[cooling-sweep] 回收 IP 数={}", n);
            }
        } catch (Exception e) {
            log.error("[cooling-sweep] 出错: {}", e.getMessage());
        }
    }
}
