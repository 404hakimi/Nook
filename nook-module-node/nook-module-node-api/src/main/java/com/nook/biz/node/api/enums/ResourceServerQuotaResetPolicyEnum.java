package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流量周期重置策略
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceServerQuotaResetPolicyEnum {

    MONTHLY("MONTHLY", "按月重置(我方重置日)"),
    FIXED("FIXED", "永不重置"),
    ;

    private final String state;
    private final String label;

    public static ResourceServerQuotaResetPolicyEnum fromState(String state) {
        if (state == null) return null;
        for (ResourceServerQuotaResetPolicyEnum e : values()) {
            if (e.state.equals(state)) return e;
        }
        return null;
    }

    public boolean matches(String state) { return this.state.equals(state); }
}
