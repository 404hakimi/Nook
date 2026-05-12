package com.nook.biz.node.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * IP 池部署模式枚举.
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceIpPoolProvisionModeEnum {

    /** 自部署: SSH 入库后可一键部署 SOCKS5. */
    SELF_DEPLOY(1, "自部署"),
    /** 第三方现成 SOCKS5; 入库即可分配. */
    EXTERNAL(2, "第三方"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(ResourceIpPoolProvisionModeEnum::getMode).toArray(Integer[]::new);

    public static final Integer MIN_VALUE = SELF_DEPLOY.mode;
    public static final Integer MAX_VALUE = EXTERNAL.mode;

    private final Integer mode;
    private final String label;

    public static ResourceIpPoolProvisionModeEnum fromMode(Integer mode) {
        if (mode == null) return null;
        for (ResourceIpPoolProvisionModeEnum e : values()) {
            if (e.mode.equals(mode)) return e;
        }
        return null;
    }

    public boolean matches(Integer mode) {
        return this.mode.equals(mode);
    }
}
