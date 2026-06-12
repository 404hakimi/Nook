package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 服务器装机生命周期; 流转: 装机中 → 待上线 → 运行中 → 已退役, 仅运行中参与选址候选
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceServerLifecycleEnum {

    INSTALLING("INSTALLING", "装机中"),
    READY("READY", "待上线"),
    LIVE("LIVE", "运行中"),
    RETIRED("RETIRED", "已退役"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ResourceServerLifecycleEnum::getState).toArray(String[]::new);

    private final String state;
    private final String label;

    public static ResourceServerLifecycleEnum fromState(String state) {
        if (state == null) return null;
        for (ResourceServerLifecycleEnum e : values()) {
            if (e.state.equals(state)) return e;
        }
        return null;
    }

    public boolean matches(String state) {
        return this.state.equals(state);
    }

    /** 合法流转: 当前状态 → 可达目标集合; 未列出的组合一律拒. */
    private static final Map<ResourceServerLifecycleEnum, Set<ResourceServerLifecycleEnum>> TRANSITIONS = Map.of(
            INSTALLING, EnumSet.of(READY),
            READY, EnumSet.of(INSTALLING, LIVE, RETIRED),
            LIVE, EnumSet.of(READY, RETIRED),
            RETIRED, EnumSet.of(LIVE)
    );

    /**
     * 是否允许从当前状态流转到目标状态
     *
     * @param target 目标状态
     * @return 是否允许
     */
    public boolean canTransitionTo(ResourceServerLifecycleEnum target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
