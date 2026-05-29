package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订阅业务状态; DB 字段 trade_subscription.status.
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradeSubscriptionStatusEnum {

    ACTIVE("ACTIVE", "生效中"),
    SUSPENDED("SUSPENDED", "已暂停(流量用尽)"),
    EXPIRED("EXPIRED", "已过期"),
    CANCELLED("CANCELLED", "已取消"),
    ;

    private final String state;
    private final String label;

    public static TradeSubscriptionStatusEnum fromState(String state) {
        if (state == null) {
            return null;
        }
        for (TradeSubscriptionStatusEnum e : values()) {
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
