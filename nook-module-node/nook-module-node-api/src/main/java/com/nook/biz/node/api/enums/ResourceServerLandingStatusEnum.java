package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 落地节点占用状态; DB 字段 resource_server_landing.status
 *
 * <p>状态机: AVAILABLE →(占用)→ OCCUPIED →(退订)→ AVAILABLE (RESERVED 为预留态, 暂未启用).
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceServerLandingStatusEnum {

    AVAILABLE("AVAILABLE", "可分配"),
    RESERVED("RESERVED", "预占中"),
    OCCUPIED("OCCUPIED", "已占用"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ResourceServerLandingStatusEnum::getState).toArray(String[]::new);

    private final String state;
    private final String label;

    public static ResourceServerLandingStatusEnum fromState(String state) {
        if (state == null) return null;
        for (ResourceServerLandingStatusEnum e : values()) {
            if (e.state.equals(state)) return e;
        }
        return null;
    }

    public boolean matches(String state) {
        return this.state.equals(state);
    }
}
