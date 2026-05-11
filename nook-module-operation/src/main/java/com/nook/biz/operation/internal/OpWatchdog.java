package com.nook.biz.operation.internal;

import com.nook.biz.operation.mapper.OpLogMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 单 op 超时守护; worker 在调 handler 前后 schedule / cancel.
 *
 * <p>超时触发流程:
 * <pre>
 *   1. CAS RUNNING → TIMED_OUT, 清 active_key
 *   2. (可选) 通知外部 "interrupter" 切断 op 持有的资源 (如 SshSession)
 *   3. worker 那侧的 SSH 调用因连接断而抛 IOException, catch 后 markFailed 因 CAS 失败 (status 已变) 自动跳过
 * </pre>
 *
 * @author nook
 */
@Slf4j
class OpWatchdog {

    /** 给外部注入的"超时时切资源"钩子; 业务侧可注册 OpType → 切 SshSession 的 callback */
    interface TimeoutInterrupter {
        void onTimeout(String opId, String serverId);
    }

    private final ScheduledExecutorService scheduler;
    private final OpLogMapper opLogMapper;
    private final TimeoutInterrupter interrupter;
    private final ConcurrentMap<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    OpWatchdog(ScheduledExecutorService scheduler, OpLogMapper opLogMapper, TimeoutInterrupter interrupter) {
        this.scheduler = scheduler;
        this.opLogMapper = opLogMapper;
        this.interrupter = interrupter;
    }

    void schedule(String opId, String serverId, Duration timeout) {
        ScheduledFuture<?> f = scheduler.schedule(
                () -> onTimeout(opId, serverId, timeout),
                timeout.toMillis(), TimeUnit.MILLISECONDS);
        scheduled.put(opId, f);
    }

    void cancel(String opId) {
        ScheduledFuture<?> f = scheduled.remove(opId);
        if (f != null) f.cancel(false);
    }

    private void onTimeout(String opId, String serverId, Duration timeout) {
        scheduled.remove(opId);
        int affected = opLogMapper.casRunningToTimedOut(
                opId, LocalDateTime.now(),
                "执行超过 " + timeout.toSeconds() + "s 被强切");
        if (affected == 0) {
            return;
        }
        log.warn("[op-watchdog] opId={} server={} 超时被强切, timeout={}s",
                opId, serverId, timeout.toSeconds());
        if (interrupter != null) {
            try {
                interrupter.onTimeout(opId, serverId);
            } catch (Exception e) {
                log.error("[op-watchdog] interrupter 回调异常 opId={}", opId, e);
            }
        }
    }
}
