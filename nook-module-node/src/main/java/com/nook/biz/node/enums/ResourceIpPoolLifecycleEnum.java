package com.nook.biz.node.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * IP 装机生命周期; DB 字段 resource_ip_pool.lifecycle_state.
 *
 * <p>状态流转: INSTALLING → READY → LIVE → RETIRED.
 * 仅 LIVE 状态参与 allocator 候选; RETIRED 老 IP 老客户走手动换 IP.
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceIpPoolLifecycleEnum {

    INSTALLING("INSTALLING", "装机中"),
    READY("READY", "待上线"),
    LIVE("LIVE", "运行中"),
    RETIRED("RETIRED", "已退役"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ResourceIpPoolLifecycleEnum::getState).toArray(String[]::new);

    private final String state;
    private final String label;

    public static ResourceIpPoolLifecycleEnum fromState(String state) {
        if (state == null) return null;
        for (ResourceIpPoolLifecycleEnum e : values()) {
            if (e.state.equals(state)) return e;
        }
        return null;
    }

    public boolean matches(String state) {
        return this.state.equals(state);
    }
}
