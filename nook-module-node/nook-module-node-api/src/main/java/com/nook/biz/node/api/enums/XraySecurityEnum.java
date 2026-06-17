package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Xray inbound 安全层类型
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum XraySecurityEnum {

    NONE("none", "无加密"),
    TLS("tls", "TLS"),
    REALITY("reality", "REALITY"),
    ;

    /** DB 存储值, 全小写. */
    private final String code;

    /** 展示标签. */
    private final String label;

    public static XraySecurityEnum fromCode(String code) {
        if (code == null) return null;
        for (XraySecurityEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) return e;
        }
        return null;
    }

    public boolean matches(String code) {
        return this.code.equalsIgnoreCase(code);
    }
}
