package com.nook.biz.operation.api.spi;

import com.nook.biz.operation.api.ProgressSink;

/**
 * 传给 OperationHandler.execute 的上下文; handler 只通过 ctx 看入参 + 报进度, 不依赖 internal.
 *
 * <p>继承 ProgressSink 让 service 直接接受 ctx 当 sink 用 — 业务 service 无须 import OperationContext.
 *
 * @author nook
 */
public interface OperationContext extends ProgressSink {

    /** op_log.id (32 位无连字符 UUID) */
    String opId();

    /** 目标 server id */
    String serverId();

    /** 子资源 id (如 client_id); server 级 op 返 null */
    String targetId();

    /** 入参 JSON (handler 自行反序列化) */
    String paramsJson();

    /**
     * 报进度; 写 DB 的 current_step / progress_pct / last_message (Stage 2 还会推 WS, 现在仅 DB).
     *
     * @param step        当前步骤描述
     * @param progressPct 进度 0-100
     */
    void report(String step, int progressPct);

    /**
     * 报进度 + 自定义消息.
     *
     * @param step        当前步骤描述
     * @param progressPct 进度 0-100
     * @param message     用户可见消息
     */
    void report(String step, int progressPct, String message);
}
