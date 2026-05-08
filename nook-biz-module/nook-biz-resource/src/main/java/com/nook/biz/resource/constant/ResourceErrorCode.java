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
    SERVER_GRPC_FIELDS_REQUIRED(5006, "xrayGrpcHost / xrayGrpcPort 必填"),
    SERVER_SSH_AUTH_REQUIRED(5007, "需提供 sshPassword 或 sshPrivateKey 之一"),
    ;

    private final int code;
    private final String message;
}
