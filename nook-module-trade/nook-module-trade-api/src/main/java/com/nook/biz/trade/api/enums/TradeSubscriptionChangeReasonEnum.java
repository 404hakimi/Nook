package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订阅换机原因枚举
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum TradeSubscriptionChangeReasonEnum {

    OPEN("OPEN", "初始开通"),
    FAULT("FAULT", "机器故障"),
    TRAFFIC_EXHAUSTED("TRAFFIC_EXHAUSTED", "流量耗尽"),
    CUSTOMER_REQUEST("CUSTOMER_REQUEST", "客户换IP"),
    MANUAL("MANUAL", "手动调整"),
    RELEASE("RELEASE", "退订释放"),
    ;

    private final String state;
    private final String label;

    public static TradeSubscriptionChangeReasonEnum fromState(String state) {
        if (state == null) {
            return null;
        }
        for (TradeSubscriptionChangeReasonEnum e : values()) {
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
