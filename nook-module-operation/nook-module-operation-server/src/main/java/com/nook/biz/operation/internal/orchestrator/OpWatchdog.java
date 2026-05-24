package com.nook.biz.operation.internal.orchestrator;

import com.nook.biz.operation.api.event.OpProgressEvent;
import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.internal.progress.ws.OpProgressHub;
import com.nook.biz.operation.dal.mysql.mapper.OpLogMapper;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
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
public class OpWatchdog {

    /** 给外部注入的"超时时切资源"钩子; 业务侧可注册 OpType → 切 SshSession 的 callback */
    public interface TimeoutInterrupter {
        void onTimeout(String opId, String serverId);
    }

    private final ScheduledExecutorService scheduler;
    private final OpLogMapper opLogMapper;
    private final TimeoutInterrupter interrupter;
    private final OpProgressHub hub;
    private final ConcurrentMap<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public OpWatchdog(ScheduledExecutorService scheduler, OpLogMapper opLogMapper,
               TimeoutInterrupter interrupter, OpProgressHub hub) {
        this.scheduler = scheduler;
        this.opLogMapper = opLogMapper;
        this.interrupter = interrupter;
        this.hub = hub;
    }

    void schedule(String opId, String serverId, Duration timeout) {
        ScheduledFuture<?> f = scheduler.schedule(
                () -> onTimeout(opId, serverId, timeout),
                timeout.toMillis(), TimeUnit.MILLISECONDS);
        scheduled.put(opId, f);
        log.debug("[op-watchdog] schedule opId={} server={} timeout={}s", opId, serverId, timeout.toSeconds());
    }

    void cancel(String opId) {
        ScheduledFuture<?> f = scheduled.remove(opId);
        if (f != null) {
            f.cancel(false);
            log.debug("[op-watchdog] cancel opId={}", opId);
        }
    }

    private void onTimeout(String opId, String serverId, Duration timeout) {
        scheduled.remove(opId);
        String reason = "执行超过 " + timeout.toSeconds() + "s 被强切";
        int affected = opLogMapper.casRunningToTimedOut(opId, LocalDateTime.now(), reason);
        if (affected == 0) {
            return;
        }
        log.warn("[op-watchdog] opId={} server={} 超时被强切, timeout={}s",
                opId, serverId, timeout.toSeconds());
        // 推一条 TIMED_OUT 终态事件; 失败查 DB 的 row 拿到 opType / 字段补全 — 但 selectById 一次, 不阻塞 worker
        if (hub != null) {
            OpLogDO row = opLogMapper.selectById(opId);
            hub.broadcast(OpProgressEvent.builder()
                    .opId(opId)
                    .serverId(serverId)
                    .opType(row == null ? null : row.getOpType())
                    .status(OpStatus.TIMED_OUT)
                    .currentStep("已超时")
                    .errorCode("TIMED_OUT")
                    .errorMsg(reason)
                    .timestamp(System.currentTimeMillis())
                    .build());
        }
        if (interrupter != null) {
            try {
                interrupter.onTimeout(opId, serverId);
            } catch (Exception e) {
                log.error("[op-watchdog] interrupter 回调异常 opId={}", opId, e);
            }
        }
    }
}
