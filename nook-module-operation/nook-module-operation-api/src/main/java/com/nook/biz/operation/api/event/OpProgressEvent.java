package com.nook.biz.operation.api.event;

import com.nook.biz.operation.api.OpStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 单条 op 的进度事件; orchestrator / context 内部 publish, WebSocket / SSE Hub 订阅推前端.
 *
 * <p>不依赖 Spring 的 ApplicationEvent 是为了让事件类型自由序列化 (fastjson2 直接 JSON),
 * WebSocket 推送时不需要再拷贝一遍字段.
 *
 * @author nook
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OpProgressEvent {

    /** op_log.id */
    private String opId;

    private String serverId;

    /** OpType.name() 字符串 */
    private String opType;

    /** 当前状态; 终态时同时发一条 status 切换事件 (DONE/FAILED/...) */
    private OpStatus status;

    /** 当前步骤描述 */
    private String currentStep;

    /** 0-100 */
    private Integer progressPct;

    /** handler 透传的用户可见消息; 可为 null */
    private String message;

    /** 终态时的错误码 (FAILED/TIMED_OUT) */
    private String errorCode;

    /** 终态时的错误消息 */
    private String errorMsg;

    /** 服务器发送时刻毫秒, 给前端做去重 / 排序用 */
    private long timestamp;
}
