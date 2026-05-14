package com.nook.biz.operation.api.spi;

import com.nook.biz.operation.api.OpProgressSink;

/**
 * 传给 OpHandler.execute 的上下文; handler 只通过 ctx 看入参 + 报进度, 不依赖 internal.
 *
 * <p>继承 OpProgressSink 让 service 直接接受 ctx 当 sink 用 — 业务 service 无须 import OpContext.
 *
 * @author nook
 */
public interface OpContext extends OpProgressSink {

    /** op_log.id (32 位无连字符 UUID) */
    String opId();

    /** 目标 server id */
    String serverId();

    /** 子资源 id (如 client_id); server 级 op 返 null */
    String targetId();

    /** 入参 JSON (handler 自行反序列化) */
    String paramsJson();

    /** 报进度 + 用户可见消息 (无 message 版由 OpProgressSink 继承). */
    void report(String step, int progressPct, String message);
}
