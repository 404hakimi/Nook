package com.nook.biz.operation.internal;

import com.nook.biz.operation.api.EnqueueRequest;
import com.nook.biz.operation.api.OpErrorCode;
import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.api.OperationHandler;
import com.nook.biz.operation.api.OperationOrchestrator;
import com.nook.biz.operation.config.OpTimeoutProperties;
import com.nook.biz.operation.mapper.OpLogMapper;
import com.nook.biz.operation.persistence.OpLog;
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
 * OperationOrchestrator 默认实现; 入队 + 启动 worker + 取消 + sync 等待.
 *
 * <p>互斥靠 ServerSlot.workerActive 的 CAS, 不靠 Lock 关键字; 见 ServerSlot 文档.
 *
 * @author nook
 */
@Slf4j
class DefaultOperationOrchestrator implements OperationOrchestrator {

    private final OpLogMapper opLogMapper;
    private final HandlerRegistry handlerRegistry;
    private final OpTimeoutProperties timeoutProps;
    private final OpWatchdog watchdog;
    private final ExecutorService workerPool;

    private final ConcurrentMap<String, ServerSlot> slots = new ConcurrentHashMap<>();
    /** 同步等待 future: opId → 调用方在等的 future; processOne 完成时 complete 它 */
    private final ConcurrentMap<String, CompletableFuture<Object>> waiters = new ConcurrentHashMap<>();

    DefaultOperationOrchestrator(OpLogMapper opLogMapper,
                                 HandlerRegistry handlerRegistry,
                                 OpTimeoutProperties timeoutProps,
                                 OpWatchdog watchdog,
                                 ExecutorService workerPool) {
        this.opLogMapper = opLogMapper;
        this.handlerRegistry = handlerRegistry;
        this.timeoutProps = timeoutProps;
        this.watchdog = watchdog;
        this.workerPool = workerPool;
    }

    @Override
    public String enqueue(EnqueueRequest req) {
        return enqueueInternal(req);
    }

    @Override
    public <T> T submitAndWait(EnqueueRequest req, Duration waitTimeout, Class<T> resultType) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        // 必须先放 waiter 再 enqueue, 否则 worker 跑得极快时 complete 找不到 future
        // 走两步: 先生成 opId 占位, 但 enqueue 内部 insert 之后才有 id;
        // 改用先 enqueue 再放 waiter, 容忍极小概率 worker 已完成 (此时 waiter 不存在, processOne 自动跳过, 这里 future.get 会超时)
        // 实际中 worker 调度 + handler 至少几毫秒, future.put 1us, 不会真撞上 — 但为绝对安全, 我们改为 enqueue 时同步 put waiter
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
            CompletableFuture<Object> f = waiters.get(opId);
            if (f != null) {
                f.completeExceptionally(new BusinessException(OpErrorCode.OP_NOT_CANCELLABLE, opId));
            }
            return true;
        }
        return false;
    }

    private String enqueueInternal(EnqueueRequest req) {
        return enqueueInternal(req, null);
    }

    /**
     * @param waiter 非空时把 future 注册到 waiters; submitAndWait 同步路径用
     */
    private String enqueueInternal(EnqueueRequest req, CompletableFuture<Object> waiter) {
        validate(req);
        String activeKey = buildActiveKey(req.getServerId(), req.getOpType(), req.getTargetId());
        if (req.isAllowDuplicate()) {
            activeKey = activeKey + "|" + System.nanoTime();
        }
        OpLog row = OpLog.builder()
                .serverId(req.getServerId())
                .opType(req.getOpType())
                .targetId(req.getTargetId())
                .operator(req.getOperator())
                .paramsJson(req.getParamsJson())
                .status(OpStatus.QUEUED)
                .activeKey(activeKey)
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
        ServerSlot slot = slots.computeIfAbsent(req.getServerId(), k -> new ServerSlot());
        slot.queue.offer(opId);
        tryStartWorker(req.getServerId(), slot);
        return opId;
    }

    /**
     * CAS workerActive(false→true) 成功的线程负责起 worker; 其余直接返回, 已有 worker 在跑.
     */
    private void tryStartWorker(String serverId, ServerSlot slot) {
        if (slot.workerActive.compareAndSet(false, true)) {
            workerPool.submit(() -> runLoop(serverId, slot));
        }
    }

    /**
     * 单 server 的 worker 主循环; 不变式见 ServerSlot 文档.
     *
     * <p>退出时的"扫一眼 + 抢回来"模式防止 enqueue-vs-exit race 丢任务.
     */
    private void runLoop(String serverId, ServerSlot slot) {
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
        OpLog op = opLogMapper.selectById(opId);
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
        String opType = op.getOpType();
        watchdog.schedule(opId, serverId, timeoutProps.of(opType));
        Object result = null;
        Throwable error = null;
        try {
            OperationHandler handler = handlerRegistry.resolve(opType);
            result = handler.execute(new DefaultOperationContext(op, opLogMapper));
            int done = opLogMapper.casRunningToDone(opId, LocalDateTime.now());
            if (done == 0) {
                error = new BusinessException(OpErrorCode.OP_TIMED_OUT, opId);
                log.warn("[op] opId={} handler 成功返回但状态已非 RUNNING (大概率超时被切)", opId);
            }
        } catch (BusinessException be) {
            opLogMapper.casRunningToFailed(opId, LocalDateTime.now(),
                    String.valueOf(be.getCode()), be.getMessage());
            log.warn("[op] opId={} 业务失败 code={} msg={}", opId, be.getCode(), be.getMessage());
            error = be;
        } catch (Throwable t) {
            opLogMapper.casRunningToFailed(opId, LocalDateTime.now(),
                    "INTERNAL", String.valueOf(t.getMessage()));
            log.error("[op] opId={} type={} 异常", opId, opType, t);
            error = t;
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

    private static void validate(EnqueueRequest req) {
        if (req == null) throw new IllegalArgumentException("EnqueueRequest 为 null");
        if (req.getServerId() == null || req.getServerId().isBlank())
            throw new IllegalArgumentException("serverId 为空");
        if (req.getOpType() == null || req.getOpType().isBlank())
            throw new IllegalArgumentException("opType 为空");
    }

    private static String buildActiveKey(String serverId, String opType, String targetId) {
        return serverId + "|" + opType + "|" + (targetId == null ? "" : targetId);
    }
}
