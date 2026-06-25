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

    public boolean matches(String state) { return this.state.equals(state); }
}
