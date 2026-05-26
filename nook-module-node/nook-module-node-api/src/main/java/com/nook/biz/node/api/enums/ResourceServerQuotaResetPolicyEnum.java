package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流量周期重置策略; DB 字段 resource_server_capacity.quota_reset_policy
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceServerQuotaResetPolicyEnum {

    CALENDAR_MONTH("CALENDAR_MONTH", "每月 1 号重置"),
    BILLING_CYCLE("BILLING_CYCLE", "按账单日重置"),
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
