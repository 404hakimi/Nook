package com.nook.biz.system.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 后台用户角色
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum SystemUserRoleEnum {

    SUPER_ADMIN("super_admin", "超级管理员"),
    OPERATOR("operator", "运营"),
    DEVOPS("devops", "运维"),
    ;

    private final String code;
    private final String label;

    public static SystemUserRoleEnum fromCode(String code) {
        if (code == null) return null;
        for (SystemUserRoleEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public boolean matches(String code) { return this.code.equals(code); }
}
