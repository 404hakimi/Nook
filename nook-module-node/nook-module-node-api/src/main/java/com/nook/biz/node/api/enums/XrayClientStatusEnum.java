package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Xray 客户端状态
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum XrayClientStatusEnum {

    RUNNING(1, "运行"),
    STOPPED(2, "已停"),
    PENDING_SYNC(3, "待同步"),
    REMOTE_ABSENT(4, "远端不存在"),
    ;

    private final Integer code;
    private final String label;

    public static XrayClientStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (XrayClientStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public boolean matches(Integer code) { return this.code.equals(code); }
}
