package com.nook.biz.member.constant;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 会员体系错误码 (3xxx 段位). */
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    // 登录失败统一文案, 避免账户枚举攻击
    LOGIN_FAILED(3001, "邮箱或密码错误"),
    ACCOUNT_DISABLED(3002, "账户已禁用, 请联系管理员"),
    EMAIL_EXISTS(3003, "邮箱 %s 已被注册"),
    PASSWORD_TOO_WEAK(3004, "密码强度不足: 至少 8 位且需含字母 + 数字"),
    OLD_PASSWORD_MISMATCH(3005, "原密码不正确"),
    MEMBER_NOT_FOUND(3006, "会员不存在"),
    SUB_TOKEN_GENERATE_FAILED(3007, "订阅 token 生成失败, 请重试"),
    ;

    private final int code;
    private final String message;
}
