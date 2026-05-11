package com.nook.biz.operation.api;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * operation 模块错误码 (8xxx 段位).
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum OpErrorCode implements ErrorCode {

    DUPLICATE_OP(8001, "已有相同任务在队列中: opId=%s"),
    OP_NOT_CANCELLABLE(8002, "该 op 不在 QUEUED 状态, 不能取消: opId=%s"),
    HANDLER_NOT_FOUND(8003, "未找到 OpType=%s 对应的 handler"),
    OP_TIMED_OUT(8004, "op 执行超时被强切: opId=%s"),
    ;

    private final int code;
    private final String message;
}
