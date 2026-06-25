package com.nook.biz.node.api.enums;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Xray 模块错误码枚举
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum XrayErrorCode implements ErrorCode {

    BACKEND_OPERATION_FAILED(6006, "服务器 %s 操作失败: %s"),
    /** server 的 nook 状态记录不存在; 通常说明该 server 还没通过 nook 部署过 */
    SERVER_STATE_NOT_FOUND(6013, "服务器 %s 的 Xray 安装记录不存在"),
    /** 装机入参跨字段校验失败 (如 useTls=true 但 domain / tls 路径缺失) */
    SERVER_INSTALL_INVALID(6018, "xray 装机参数非法: %s"),
    ;

    private final int code;
    private final String message;
}
