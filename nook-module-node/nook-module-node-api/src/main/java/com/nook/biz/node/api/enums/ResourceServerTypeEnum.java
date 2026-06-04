package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 服务器角色
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceServerTypeEnum {

    FRONTLINE("frontline", "线路机"),
    LANDING("landing", "落地机"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ResourceServerTypeEnum::getState).toArray(String[]::new);

    private final String state;
    private final String label;

    public static ResourceServerTypeEnum fromState(String state) {
        if (state == null) return null;
        for (ResourceServerTypeEnum e : values()) {
            if (e.state.equals(state)) return e;
        }
        return null;
    }

    public boolean matches(String state) { return this.state.equals(state); }
}
