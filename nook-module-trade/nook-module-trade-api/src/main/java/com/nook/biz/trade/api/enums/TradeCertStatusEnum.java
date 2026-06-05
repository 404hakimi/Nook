package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订阅凭证期望态枚举: 后台决定该接入点该不该跑, agent 照着收敛
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradeCertStatusEnum {

    ACTIVE("ACTIVE", "应运行"),
    SUSPENDED("SUSPENDED", "应停"),
    REVOKED("REVOKED", "应移除"),
    ;

    private final String state;
    private final String label;

    public static TradeCertStatusEnum fromState(String state) {
        if (state == null) {
            return null;
        }
        for (TradeCertStatusEnum e : values()) {
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
