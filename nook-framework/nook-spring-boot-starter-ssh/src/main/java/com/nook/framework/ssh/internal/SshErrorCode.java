package com.nook.framework.ssh.internal;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SSH starter 内部错误码 (7xxx 段位); package-private, 业务侧仅通过 BusinessException 拿到, 不直接 import.
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
enum SshErrorCode implements ErrorCode {

    SSH_CREDENTIAL_INVALID(7001, "SSH 凭据不完整: %s"),
    SSH_UNREACHABLE(7002, "无法连接到 SSH server: %s"),
    SSH_COMMAND_FAILED(7003, "SSH 命令执行失败 server=%s: %s"),
    ;

    private final int code;
    private final String message;
}
