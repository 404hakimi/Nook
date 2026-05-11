package com.nook.biz.operation.api;

/**
 * 危险操作排队公共入口; 业务模块 (controller / 定时器 / 事件 listener) 只通过这个接口入队.
 *
 * <p>设计契约:
 * <ul>
 *   <li>同一 server 上活跃 op (QUEUED/RUNNING) 严格 FIFO, 互斥执行;
 *   <li>同 (serverId, opType, targetId) 三元组在活跃期间最多 1 条, 二次入队抛 OpDuplicateException;
 *   <li>跨 server 完全并行, 受 worker 线程池容量约束;
 *   <li>RUNNING 状态不允许取消; cancel(opId) 仅对 QUEUED 生效.
 * </ul>
 *
 * @author nook
 */
public interface OperationOrchestrator {

    /**
     * 入队; 同步返回 opId, 不等待执行完成 (fire-and-forget).
     *
     * @param req 入队入参
     * @return op_log.id (32 位 UUID)
     */
    String enqueue(EnqueueRequest req);

    /**
     * 入队 + 阻塞等到该 op 终态; 用于 controller 同步语义场景.
     *
     * <p>handler 抛 BusinessException 时此处会原样重抛, 调用方拿到的是底层业务错误码;
     * 超时被 watchdog 切的话抛 OP_TIMED_OUT.
     *
     * @param req           入队入参
     * @param waitTimeout   最长等待时间; 超过返 timeout 异常但 op 仍在跑
     * @param resultType    期望返回类型 (handler.execute 返回 Object 后强转)
     * @param <T>           返回类型
     * @return handler 结果
     */
    <T> T submitAndWait(EnqueueRequest req, java.time.Duration waitTimeout, Class<T> resultType);

    /**
     * 取消队列中等待执行的 op; RUNNING / DONE / FAILED 全部静默 no-op 返 false.
     *
     * @param opId op_log.id
     * @return 是否真的取消了
     */
    boolean cancelQueued(String opId);
}
