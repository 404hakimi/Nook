package com.nook.common.web.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通用错误码（1xxx 段位，跨模块共用）。 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    SUCCESS(0, "ok"),

    INTERNAL_ERROR(1000, "系统异常，请稍后重试"),
    PARAM_INVALID(1001, "参数错误: %s"),
    UNAUTHORIZED(1002, "未登录或登录已过期"),
    FORBIDDEN(1003, "无权访问"),
    NOT_FOUND(1004, "资源不存在"),
    METHOD_NOT_ALLOWED(1005, "请求方法不支持"),
    RATE_LIMITED(1006, "请求过于频繁，请稍后再试"),
    CONCURRENT_CONFLICT(1007, "操作冲突，请刷新后重试"),
    ;

    private final int code;
    private final String message;
}
