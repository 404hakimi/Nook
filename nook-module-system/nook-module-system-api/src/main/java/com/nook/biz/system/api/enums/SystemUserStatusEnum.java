package com.nook.biz.system.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 后台用户状态
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum SystemUserStatusEnum {

    NORMAL(1, "正常"),
    DISABLED(2, "禁用"),
    ;

    private final Integer code;
    private final String label;

    public static SystemUserStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (SystemUserStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public boolean matches(Integer code) { return this.code.equals(code); }
}
