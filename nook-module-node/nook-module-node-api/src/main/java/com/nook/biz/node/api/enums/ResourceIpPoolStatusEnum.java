package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * IP 占用状态 (allocator 视角); DB 字段 resource_ip_pool.status.
 *
 * <p>状态机: AVAILABLE → (RESERVED → OCCUPIED) → COOLING → AVAILABLE.
 * 仅 lifecycle_state=LIVE 期间 status 才有意义.
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceIpPoolStatusEnum {

    AVAILABLE("AVAILABLE", "可分配"),
    RESERVED("RESERVED", "预占中"),
    OCCUPIED("OCCUPIED", "已占用"),
    COOLING("COOLING", "冷却中"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ResourceIpPoolStatusEnum::getState).toArray(String[]::new);

    private final String state;
    private final String label;

    public static ResourceIpPoolStatusEnum fromState(String state) {
        if (state == null) return null;
        for (ResourceIpPoolStatusEnum e : values()) {
            if (e.state.equals(state)) return e;
        }
        return null;
    }

    public boolean matches(String state) {
        return this.state.equals(state);
    }
}
