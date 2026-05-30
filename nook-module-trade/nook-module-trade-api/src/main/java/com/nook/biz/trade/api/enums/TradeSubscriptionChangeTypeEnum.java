package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订阅换机类型枚举
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradeSubscriptionChangeTypeEnum {

    FRONTLINE("FRONTLINE", "线路机"),
    LANDING("LANDING", "落地机"),
    ;

    private final String state;
    private final String label;

    public static TradeSubscriptionChangeTypeEnum fromState(String state) {
        if (state == null) {
            return null;
        }
        for (TradeSubscriptionChangeTypeEnum e : values()) {
            if (e.state.equals(state)) {
                return e;
            }
        }
        return null;
    }

    public boolean matches(String state) {
        return this.state.equals(state);
    }
}
