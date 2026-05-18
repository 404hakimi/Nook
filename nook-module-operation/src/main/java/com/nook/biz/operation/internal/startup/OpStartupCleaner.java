package com.nook.biz.operation.internal.startup;

import com.nook.biz.operation.dal.mysql.mapper.OpLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.concurrent.CompletableFuture;

/**
 * 启动清理: 上次崩溃残留的 RUNNING 行没有内存中的 watchdog 看着了, 全部标 FAILED(WORKER_LOST).
 *
 * <p>挂在 {@link ApplicationReadyEvent} + 后台线程异步跑, 不阻塞 Spring 启动:
 * 首次 JDBC + MyBatis-Plus 元数据初始化加起来通常 10+ 秒, 顶到 PostConstruct 会拖慢整个 app start.
 *
 * <p>不做 auto-resume — 我们不是 saga, 业务侧自己重发或等 reconciler 修.
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
public class OpStartupCleaner {

    private final OpLogMapper opLogMapper;

    @EventListener(ApplicationReadyEvent.class)
    void cleanupZombiesAsync() {
        CompletableFuture.runAsync(this::doCleanup);
    }

    private void doCleanup() {
        long t0 = System.currentTimeMillis();
        try {
            int n = opLogMapper.cleanupZombieRunning();
            long cost = System.currentTimeMillis() - t0;
            if (n > 0) {
                log.warn("[op-cleaner] 启动清理: {} 条上次崩溃残留的 RUNNING 已标 FAILED(WORKER_LOST), 耗时 {}ms", n, cost);
            } else {
                log.info("[op-cleaner] 启动清理: 无残留 RUNNING, 耗时 {}ms", cost);
            }
        } catch (Exception e) {
            log.error("[op-cleaner] 启动清理失败, 跳过 (耗时 {}ms)", System.currentTimeMillis() - t0, e);
        }
    }
}
