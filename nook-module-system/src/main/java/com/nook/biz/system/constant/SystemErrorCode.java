package com.nook.biz.system.constant;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 后台系统模块错误码（2xxx 段位）。 */
@Getter
@RequiredArgsConstructor
public enum SystemErrorCode implements ErrorCode {

    // 登录失败统一文案，避免账户枚举攻击
    LOGIN_FAILED(2001, "用户名或密码错误"),
    ACCOUNT_DISABLED(2002, "账号已禁用，请联系管理员"),
    USER_NOT_FOUND(2003, "用户不存在"),
    USERNAME_EXISTS(2004, "用户名 %s 已存在"),
    PASSWORD_TOO_WEAK(2005, "密码强度不足"),
    EMAIL_EXISTS(2006, "邮箱 %s 已被使用"),
    CANNOT_DELETE_SELF(2007, "不能删除当前登录账号"),
    CANNOT_DISABLE_SELF(2008, "不能禁用当前登录账号"),
    INVALID_ROLE(2009, "无效的角色: %s"),
    ;

    private final int code;
    private final String message;
}
