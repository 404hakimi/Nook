package com.nook.biz.operation.internal;

import com.nook.biz.operation.mapper.OpLogMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动清理: 上次崩溃残留的 RUNNING 行没有内存中的 watchdog 看着了, 全部标 FAILED(WORKER_LOST).
 *
 * <p>不做 auto-resume — 我们不是 saga, 业务侧自己重发或等 reconciler 修.
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
class OpStartupCleaner {

    private final OpLogMapper opLogMapper;

    @PostConstruct
    void cleanupZombies() {
        int n = opLogMapper.cleanupZombieRunning();
        if (n > 0) {
            log.warn("[op-cleaner] 启动清理: {} 条上次崩溃残留的 RUNNING 已标 FAILED(WORKER_LOST)", n);
        }
    }
}
