package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 套餐上架状态枚举
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradePlanEnabledEnum {

    ENABLED(1, "上架"),
    DISABLED(0, "下架"),
    ;

    private final Integer code;
    private final String label;

    public static TradePlanEnabledEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TradePlanEnabledEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    public boolean matches(Integer code) {
        return this.code.equals(code);
    }

    public boolean isEnabled() {
        return this == ENABLED;
    }
}
