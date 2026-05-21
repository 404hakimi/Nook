package com.nook.framework.ssh.script.internal;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 脚本执行错误码 (7100 段位, 跟 SshErrorCode 7000 段位错开).
 * package-private, 业务侧仅通过 BusinessException 拿到, 不直接 import.
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum ScriptErrorCode implements ErrorCode {

    TEMPLATE_NOT_FOUND(7101, "脚本模板找不到: %s"),
    TEMPLATE_VAR_MISSING(7102, "脚本 %s 缺必填变量: %s"),
    ;

    private final int code;
    private final String message;
}
