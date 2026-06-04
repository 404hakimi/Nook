package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 服务器装机生命周期.
 *
 * <p>状态流转: INSTALLING → READY → LIVE → RETIRED.
 * 仅 LIVE 状态参与 allocator 候选; RETIRED 老客户走 §9.7 退役切换.
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
}
