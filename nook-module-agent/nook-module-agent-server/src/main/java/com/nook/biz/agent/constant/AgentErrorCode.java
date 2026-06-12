package com.nook.biz.agent.constant;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Agent 模块错误码枚举
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum AgentErrorCode implements ErrorCode {

    INSTALL_ROLE_INVALID(9001, "无效的装机角色: %s"),
    SERVER_TYPE_MISMATCH(9002, "服务器 %s 类型 %s 与装机角色 %s 不一致"),
    AGENT_TOKEN_MISSING(9003, "服务器 %s 缺少 agent_token, 请先在 server 入库时签发"),
    FRONTLINE_XRAY_REQUIRED(9004, "frontline 装机必须传 xrayBin + xrayApiPort (服务器 %s 可能未装 xray)"),
    ;

    private final int code;
    private final String message;
}
