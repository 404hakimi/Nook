package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流量授予类型枚举
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradeTrafficGrantTypeEnum {

    BASE("BASE", "基础"),
    ADDON("ADDON", "加购"),
    PROMO("PROMO", "活动赠送"),
    COMPENSATION("COMPENSATION", "故障补偿"),
    ;

    private final String type;
    private final String label;

    public static TradeTrafficGrantTypeEnum fromType(String type) {
        if (type == null) {
            return null;
        }
        for (TradeTrafficGrantTypeEnum e : values()) {
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
