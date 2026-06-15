package com.nook.biz.trade.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订阅业务状态枚举
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

    /** 合法流转: 当前状态 → 可达目标集合; 未列出一律拒. 已过期 → 生效中 为续费复活预留 (暂无入口). */
    private static final Map<TradeSubscriptionStatusEnum, Set<TradeSubscriptionStatusEnum>> TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(SUSPENDED, EXPIRED, CANCELLED),
            SUSPENDED, EnumSet.of(ACTIVE, EXPIRED, CANCELLED),
            EXPIRED, EnumSet.of(ACTIVE),
            CANCELLED, EnumSet.noneOf(TradeSubscriptionStatusEnum.class)
    );

    /**
     * 是否允许从当前状态流转到目标状态
     *
     * @param target 目标状态
     * @return 是否允许
     */
    public boolean canTransitionTo(TradeSubscriptionStatusEnum target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
