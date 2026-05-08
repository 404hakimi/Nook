package com.nook.biz.xray.constant;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Xray 模块错误码 (6xxx 段位)。 */
@Getter
@RequiredArgsConstructor
public enum XrayErrorCode implements ErrorCode {

    BACKEND_TYPE_UNSUPPORTED(6001, "暂不支持 backend 类型: %s"),
    SERVER_CREDENTIAL_INVALID(6002, "服务器 %s 的 backend 凭据不完整"),
    BACKEND_UNREACHABLE(6003, "无法连接到服务器 %s"),
    BACKEND_AUTH_FAILED(6004, "服务器 %s 鉴权失败 (面板账号/密码或 gRPC token 错误)"),
    BACKEND_RESPONSE_INVALID(6005, "服务器 %s 响应非法: %s"),
    BACKEND_OPERATION_FAILED(6006, "服务器 %s 操作失败: %s"),
    INBOUND_NOT_FOUND(6007, "inbound %s 不存在或未关联到任何 IP"),
    CLIENT_NOT_FOUND(6008, "client %s 不存在"),
    CLIENT_DUPLICATE(6009, "client %s 已存在"),
    GRPC_NOT_IMPLEMENTED(6010, "gRPC backend 该操作未实现: %s"),
    /** xray_inbound DB 行不存在；与 CLIENT_NOT_FOUND(远端 client 不存在)区分开 */
    INBOUND_ENTITY_NOT_FOUND(6011, "xray_inbound 行 %s 不存在"),
    ;

    private final int code;
    private final String message;
}
