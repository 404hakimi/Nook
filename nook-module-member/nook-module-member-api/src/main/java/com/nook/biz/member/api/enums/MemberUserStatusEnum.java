package com.nook.biz.member.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员状态
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum MemberUserStatusEnum {

    NORMAL(1, "正常"),
    DISABLED(2, "禁用"),
    ;

    private final Integer code;
    private final String label;

    public static MemberUserStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (MemberUserStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public boolean matches(Integer code) { return this.code.equals(code); }
}
