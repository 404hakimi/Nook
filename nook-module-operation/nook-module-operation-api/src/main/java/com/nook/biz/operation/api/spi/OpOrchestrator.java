package com.nook.biz.operation.api.spi;

import com.nook.biz.operation.api.dto.OpEnqueueRequest;

import java.time.Duration;

/**
 * 危险操作排队公共入口
 *
 * <p>设计契约: 同一 server 活跃 op 严格 FIFO 互斥; 同 (serverId, opType, targetId) 三元组活跃期最多 1 条;
 * 跨 server 并行受 worker 池容量约束; RUNNING 不允许取消, cancel 仅对 QUEUED 生效.
 *
 * @author nook
 */
public interface OpOrchestrator {

    /**
     * 入队 (fire-and-forget)
     *
     * @param req 入队入参
     * @return op_log 编号
     */
    String enqueue(OpEnqueueRequest req);

    /**
     * 入队 + 阻塞等到终态
     *
     * @param req         入队入参
     * @param waitTimeout 最长等待时间
     * @param resultType  期望返回类型
     * @param <T>         返回类型
     * @return handler 结果
     */
    <T> T submitAndWait(OpEnqueueRequest req, Duration waitTimeout, Class<T> resultType);

    /**
     * 取消排队中的 op
     *
     * @param opId op_log 编号
     * @return 是否取消成功
     */
    boolean cancelQueued(String opId);
}
