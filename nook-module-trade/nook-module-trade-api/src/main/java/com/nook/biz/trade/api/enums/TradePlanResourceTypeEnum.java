package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 套餐关联资源类型枚举
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradePlanResourceTypeEnum {

    FRONTLINE("FRONTLINE", "线路机"),
    LANDING("LANDING", "落地机"),
    ;

    private final String type;
    private final String label;

    public static TradePlanResourceTypeEnum fromType(String type) {
        if (type == null) {
            return null;
        }
        for (TradePlanResourceTypeEnum e : values()) {
            if (e.type.equals(type)) {
                return e;
            }
        }
        return null;
    }

    public boolean matches(String type) {
        return this.type.equals(type);
    }
}
