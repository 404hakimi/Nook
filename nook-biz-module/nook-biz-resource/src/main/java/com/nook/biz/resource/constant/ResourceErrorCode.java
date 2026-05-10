package com.nook.biz.resource.constant;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Resource 模块错误码 (5xxx 段位)。 */
@Getter
@RequiredArgsConstructor
public enum ResourceErrorCode implements ErrorCode {

    SERVER_NOT_FOUND(5001, "服务器 %s 不存在"),
    SERVER_NAME_DUPLICATE(5002, "服务器别名 %s 已存在"),
    SERVER_HOST_DUPLICATE(5003, "服务器主机 %s 已存在"),
    // 5007 SERVER_SSH_PASSWORD_REQUIRED 已删除 — 由 ResourceServerSaveReqVOValidator 统一抛 PARAM_INVALID

    IP_TYPE_NOT_FOUND(5101, "IP 类型 %s 不存在"),
    IP_POOL_NOT_FOUND(5102, "IP 池条目 %s 不存在"),
    IP_POOL_IP_DUPLICATE(5103, "IP %s 已存在于池中"),
    IP_POOL_NOT_AVAILABLE(5104, "IP %s 当前不可分配 (状态: %s)"),
    IP_POOL_EXHAUSTED(5105, "%s 区域 %s 类型暂无可用 IP"),
    IP_POOL_OCCUPY_CONFLICT(5106, "IP %s 抢占失败, 已被其它会员领走"),
    ;

    private final int code;
    private final String message;
}
