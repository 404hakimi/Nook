package com.nook.biz.system.constant;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 后台系统模块错误码枚举
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum SystemErrorCode implements ErrorCode {

    // 登录失败统一文案，避免账户枚举攻击
    LOGIN_FAILED(2001, "用户名或密码错误"),
    ACCOUNT_DISABLED(2002, "账号已禁用，请联系管理员"),
    USER_NOT_FOUND(2003, "用户不存在"),
    USERNAME_EXISTS(2004, "用户名 %s 已存在"),
    PASSWORD_TOO_WEAK(2005, "密码强度不足: 至少 8 位且需含字母 + 数字"),
    EMAIL_EXISTS(2006, "邮箱 %s 已被使用"),
    CANNOT_DELETE_SELF(2007, "不能删除当前登录账号"),
    INVALID_ROLE(2009, "无效的角色: %s"),
    INVALID_STATUS(2010, "无效的状态: %s"),

    IP_TYPE_NOT_FOUND(2101, "IP 类型 %s 不存在"),

    REGION_NOT_FOUND(2110, "区域 %s 不存在"),
    REGION_CODE_EXISTS(2111, "区域码 %s 已存在"),

    DOMAIN_NOT_FOUND(2120, "域名 %s 不存在"),
    DOMAIN_DUPLICATE(2121, "域名 %s 已存在"),
    DOMAIN_RENAME_BOUND(2122, "根域 %s 已被线路机绑定, 不能改域名串; 请改 Cloudflare 配置或新建域名"),
    ;

    private final int code;
    private final String message;
}
