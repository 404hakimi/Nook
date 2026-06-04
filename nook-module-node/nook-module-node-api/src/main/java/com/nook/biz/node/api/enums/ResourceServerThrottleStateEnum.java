package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 服务器限流状态
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceServerThrottleStateEnum {

    NORMAL("NORMAL", "正常"),
    THROTTLED("THROTTLED", "已触发限流"),
    ;

    private final String state;
    private final String label;

    public static ResourceServerThrottleStateEnum fromState(String state) {
        if (state == null) return null;
        for (ResourceServerThrottleStateEnum e : values()) {
            if (e.state.equals(state)) return e;
        }
        return null;
    }

    public boolean matches(String state) { return this.state.equals(state); }
}
