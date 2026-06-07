package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流量授予状态枚举
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradeQuotaStatusEnum {

    ACTIVE("ACTIVE", "生效中"),
    USED_UP("USED_UP", "已用尽"),
    EXPIRED("EXPIRED", "已过期"),
    REVOKED("REVOKED", "已撤销"),
    ;

    private final String state;
    private final String label;

    public static TradeQuotaStatusEnum fromState(String state) {
        if (state == null) {
            return null;
        }
        for (TradeQuotaStatusEnum e : values()) {
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
