package com.nook.biz.operation.api;

/**
 * 一个 opType 一个 handler; 各业务模块声明为 @Component, 启动时被 HandlerRegistry 自动收集.
 *
 * <p>实现侧只关心 recipe (做什么 + 报进度), 不需要操心锁 / audit / 超时 / 重复入队 — 都在 orchestrator 公共骨架里.
 *
 * @author nook
 */
public interface OperationHandler {

    /**
     * 该 handler 负责处理哪种 op; 返回 biz 模块 enum 的 .name() 即可.
     *
     * @return opType 字符串
     */
    String type();

    /**
     * 执行 recipe; 抛 BusinessException 表业务失败, 其他异常一律转 INTERNAL.
     *
     * <p>void op 返 null; 有返回值 op (provision / rotate / restart 等) 把结果原样返,
     * 由 orchestrator.submitAndWait 透回 controller.
     *
     * @param ctx 上下文
     * @return recipe 结果, 可为 null
     * @throws Exception 任何执行期失败
     */
    Object execute(OperationContext ctx) throws Exception;
}
