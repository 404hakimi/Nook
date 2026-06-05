package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订阅凭证来源枚举
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradeCertSourceEnum {

    BASE("BASE", "基础"),
    ADDON("ADDON", "加购"),
    ;

    private final String source;
    private final String label;

    public static TradeCertSourceEnum fromSource(String source) {
        if (source == null) {
            return null;
        }
        for (TradeCertSourceEnum e : values()) {
            if (e.source.equals(source)) {
                return e;
            }
        }
        return null;
    }

    public boolean matches(String source) {
        return this.source.equals(source);
    }
}
