package com.nook.biz.operation.internal.orchestrator;

import com.nook.biz.operation.api.dto.OpEnqueueRequest;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.enums.OpErrorCode;
import com.nook.biz.operation.api.event.OpProgressEvent;
import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.api.spi.OpHandler;
import com.nook.biz.operation.api.spi.OpOrchestrator;
import com.nook.biz.operation.internal.progress.ws.OpProgressHub;
import com.nook.biz.operation.dal.mysql.mapper.OpLogMapper;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpOrchestrator 默认实现
 *
 * @author nook
 */
@Slf4j
public class DefaultOpOrchestrator implements OpOrchestrator {

    private final OpLogMapper opLogMapper;
    private final OpHandlerRegistry handlerRegistry;
    private final OpConfigResolver opConfigResolver;
    private final OpWatchdog watchdog;
    private final ExecutorService workerPool;
    private final OpProgressHub hub;

    private final ConcurrentMap<String, OpServerSlot> slots = new ConcurrentHashMap<>();
    /** 同步等待 future: opId → 调用方在等的 future; processOne 完成时 complete 它 */
    private final ConcurrentMap<String, CompletableFuture<Object>> waiters = new ConcurrentHashMap<>();

    public DefaultOpOrchestrator(OpLogMapper opLogMapper,
                                 OpHandlerRegistry handlerRegistry,
                                 OpConfigResolver opConfigResolver,
                                 OpWatchdog watchdog,
                                 ExecutorService workerPool,
                                 OpProgressHub hub) {
        this.opLogMapper = opLogMapper;
        this.handlerRegistry = handlerRegistry;
        this.opConfigResolver = opConfigResolver;
        this.watchdog = watchdog;
        this.workerPool = workerPool;
        this.hub = hub;
    }

    @Override
    public String enqueue(OpEnqueueRequest req) {
        return enqueueInternal(req);
    }

    @Override
    public <T> T submitAndWait(OpEnqueueRequest req, Duration waitTimeout, Class<T> resultType) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        // waiter 与 enqueue 必须在同一原子操作内注册, 防 worker 极快完成时 complete 找不到 future
        String opId = enqueueInternal(req, future);
        try {
            Object value = future.get(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (value == null || resultType.isInstance(value)) {
                return resultType.cast(value);
            }
            throw new IllegalStateException(
                    "handler 返回类型与 resultType 不匹配: expected=" + resultType.getName()
                            + " actual=" + value.getClass().getName());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BusinessException(OpErrorCode.OP_TIMED_OUT, opId);
        } catch (TimeoutException te) {
            throw new BusinessException(OpErrorCode.OP_TIMED_OUT, opId);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof BusinessException be) throw be;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } finally {
            waiters.remove(opId);
        }
    }

    @Override
    public boolean cancelQueued(String opId) {
        int n = opLogMapper.casQueuedToCancelled(opId, LocalDateTime.now());
        if (n > 0) {
            log.info("[op] 已取消 opId={}", opId);
            // 推一条终态事件让前端进度条立即跳走
            OpLogDO row = opLogMapper.selectById(opId);
            if (row != null) {
                broadcast(row, OpStatus.CANCELLED, "已取消", null, null, null);
            }
            CompletableFuture<Object> f = waiters.get(opId);
            if (f != null) {
                f.completeExceptionally(new BusinessException(OpErrorCode.OP_NOT_CANCELLABLE, opId));
            }
            return true;
        }
        return false;
    }

    private String enqueueInternal(OpEnqueueRequest req) {
        return enqueueInternal(req, null);
    }

    /** waiter 非空 = submitAndWait 同步路径, 把 future 注册到 waiters 等待 processOne 完成 */
    private String enqueueInternal(OpEnqueueRequest req, CompletableFuture<Object> waiter) {
        validate(req);
        // admin 可在 op_config 页停用某 opType, 入队即拒
        if (!opConfigResolver.isEnabled(req.getOpType())) {
            throw new BusinessException(OpErrorCode.OP_DISABLED, req.getOpType());
        }
        String activeKey = buildActiveKey(req.getServerId(), req.getOpType(), req.getTargetId());
        if (req.isAllowDuplicate()) {
            activeKey = activeKey + "|" + System.nanoTime();
        }
        OpLogDO row = OpLogDO.builder()
                .serverId(req.getServerId())
                .opType(req.getOpType())
                .targetId(req.getTargetId())
                .operator(req.getOperator())
                .paramsJson(req.getParamsJson())
                .status(OpStatus.QUEUED)
                .activeKey(activeKey)
                .currentStep("等待中")
                .progressPct(0)
                .enqueuedAt(LocalDateTime.now())
                .build();
        try {
            opLogMapper.insert(row);
        } catch (DuplicateKeyException dke) {
            String existingId = opLogMapper.findActiveIdByKey(activeKey);
            log.info("[op] 重复入队 key={} existingId={}", activeKey, existingId);
            throw new BusinessException(OpErrorCode.DUPLICATE_OP, dke, existingId);
        }
        String opId = row.getId();
        if (waiter != null) {
            waiters.put(opId, waiter);
        }
        log.info("[op] 入队 opId={} server={} type={} target={} operator={}",
                opId, req.getServerId(), req.getOpType(), req.getTargetId(), req.getOperator());
        broadcast(row, OpStatus.QUEUED, "等待中", 0, null, null);
        OpServerSlot slot = slots.computeIfAbsent(req.getServerId(), k -> new OpServerSlot());
        slot.queue.offer(opId);
        tryStartWorker(req.getServerId(), slot);
        return opId;
    }

    /**
     * CAS workerActive(false→true) 成功的线程负责起 worker; 其余直接返回, 已有 worker 在跑.
     */
    private void tryStartWorker(String serverId, OpServerSlot slot) {
        if (slot.workerActive.compareAndSet(false, true)) {
            workerPool.submit(() -> runLoop(serverId, slot));
        }
    }

    /**
     * 单 server 的 worker 主循环; 不变式见 OpServerSlot 文档.
     *
     * <p>退出时的"扫一眼 + 抢回来"模式防止 enqueue-vs-exit race 丢任务.
     */
    private void runLoop(String serverId, OpServerSlot slot) {
        try {
            String opId;
            while ((opId = slot.queue.poll()) != null) {
                processOne(opId, serverId);
            }
        } finally {
            slot.workerActive.set(false);
            if (!slot.queue.isEmpty() && slot.workerActive.compareAndSet(false, true)) {
                workerPool.submit(() -> runLoop(serverId, slot));
            }
        }
    }

    /**
     * 处理单个 op: CAS QUEUED→RUNNING → 起 watchdog → 调 handler → 终态 CAS + 通知 waiter.
     */
    private void processOne(String opId, String serverId) {
        OpLogDO op = opLogMapper.selectById(opId);
        if (op == null) {
            log.warn("[op] opId={} 不存在, 跳过", opId);
            completeWaiterError(opId, new BusinessException(OpErrorCode.HANDLER_NOT_FOUND, "opId=" + opId));
            return;
        }
        int started = opLogMapper.casQueuedToRunning(opId, LocalDateTime.now());
        if (started == 0) {
            log.debug("[op] opId={} 未能进入 RUNNING (可能已取消), 跳过", opId);
            return;
        }
        broadcast(op, OpStatus.RUNNING, "已开始", 5, null, null);
        String opType = op.getOpType();
        // watchdog 覆盖全部重试总时长; admin 调 exec_timeout 时应按"含重试"评估
        watchdog.schedule(opId, serverId, opConfigResolver.getExecTimeout(opType));
        int maxRetry = opConfigResolver.getMaxRetry(opType);
        Object result = null;
        Throwable error = null;
        try {
            OpHandler handler = handlerRegistry.resolve(opType);
            for (int attempt = 0; attempt <= maxRetry; attempt++) {
                if (attempt > 0) {
                    broadcast(op, OpStatus.RUNNING, "重试 " + attempt + "/" + maxRetry, 5, null, null);
                    log.info("[op] retry opId={} attempt={}/{}", opId, attempt, maxRetry);
                }
                try {
                    result = handler.execute(new DefaultOpContext(op, opLogMapper, hub));
                    error = null;
                    break;
                } catch (BusinessException be) {
                    // 业务异常 (校验类) 不重试: 重试同样错误没意义, 反而拖慢 fail-fast
                    error = be;
                    break;
                } catch (Throwable t) {
                    error = t;
                    log.warn("[op] opId={} attempt={} 失败, 待重试: {}", opId, attempt, t.getMessage());
                }
            }
            if (error == null) {
                int done = opLogMapper.casRunningToDone(opId, LocalDateTime.now());
                if (done == 0) {
                    error = new BusinessException(OpErrorCode.OP_TIMED_OUT, opId);
                    log.warn("[op] opId={} handler 成功返回但状态已非 RUNNING (大概率超时被切)", opId);
                } else {
                    broadcast(op, OpStatus.DONE, "已完成", 100, null, null);
                }
            } else if (error instanceof BusinessException be) {
                opLogMapper.casRunningToFailed(opId, LocalDateTime.now(),
                        String.valueOf(be.getCode()), be.getMessage());
                log.warn("[op] opId={} 业务失败 code={} msg={}", opId, be.getCode(), be.getMessage());
                broadcast(op, OpStatus.FAILED, "已失败", null,
                        String.valueOf(be.getCode()), be.getMessage());
            } else {
                opLogMapper.casRunningToFailed(opId, LocalDateTime.now(),
                        "INTERNAL", String.valueOf(error.getMessage()));
                log.error("[op] opId={} type={} 异常", opId, opType, error);
                broadcast(op, OpStatus.FAILED, "已失败", null, "INTERNAL", String.valueOf(error.getMessage()));
            }
        } finally {
            watchdog.cancel(opId);
            if (error != null) {
                completeWaiterError(opId, error);
            } else {
                completeWaiterValue(opId, result);
            }
        }
    }

    private void completeWaiterValue(String opId, Object value) {
        CompletableFuture<Object> f = waiters.get(opId);
        if (f != null) f.complete(value);
    }

    private void completeWaiterError(String opId, Throwable t) {
        CompletableFuture<Object> f = waiters.get(opId);
        if (f != null) f.completeExceptionally(t);
    }

    /** WS 广播; hub null = 无 WS 环境 (测试 / 离线), 推送异常静默吞防污染主流程 */
    private void broadcast(OpLogDO op, OpStatus status, String step, Integer pct,
                           String errorCode, String errorMsg) {
        if (hub == null) return;
        try {
            hub.broadcast(OpProgressEvent.builder()
                    .opId(op.getId())
                    .serverId(op.getServerId())
                    .opType(op.getOpType())
                    .status(status)
                    .currentStep(step)
                    .progressPct(pct)
                    .errorCode(errorCode)
                    .errorMsg(errorMsg)
                    .timestamp(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.warn("[op] 进度广播失败 opId={} status={}: {}", op.getId(), status, e.getMessage());
        }
    }

    private static void validate(OpEnqueueRequest req) {
        if (req == null) throw new IllegalArgumentException("OpEnqueueRequest 为 null");
        if (req.getServerId() == null || req.getServerId().isBlank())
            throw new IllegalArgumentException("serverId 为空");
        if (req.getOpType() == null || req.getOpType().isBlank())
            throw new IllegalArgumentException("opType 为空");
    }

    private static String buildActiveKey(String serverId, String opType, String targetId) {
        return serverId + "|" + opType + "|" + (targetId == null ? "" : targetId);
    }
}
