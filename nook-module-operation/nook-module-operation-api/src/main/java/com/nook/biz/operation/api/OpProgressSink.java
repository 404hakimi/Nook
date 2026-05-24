package com.nook.biz.operation.api;

/**
 * 业务 service 接受的可选进度回调; OpContext 实现了它, 非 op 场景也能传 lambda / noop.
 *
 * <p>设计目的: service 内部要打中间步骤进度时, 不必硬依赖 OpContext (它带了 opId / paramsJson 等
 * service 用不到的字段); 只要个函数式 sink 就够.
 *
 * <p>静态工厂 {@link #noop()} 给非 op 场景用 — service 调用方不在 op 流水里时传它即可.
 *
 * @author nook
 */
@FunctionalInterface
public interface OpProgressSink {

    /**
     * 报进度.
     *
     * @param step        当前步骤描述
     * @param progressPct 0-100; 越界由实现自行 clamp
     */
    void report(String step, int progressPct);

    /** noop sink; 非 op 场景的 service 调用传它, 内部 if-null 判断省了. */
    static OpProgressSink noop() {
        return (step, pct) -> { };
    }
}
