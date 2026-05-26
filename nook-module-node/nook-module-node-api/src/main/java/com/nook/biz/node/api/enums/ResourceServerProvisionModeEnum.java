package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 落地机部署模式; DB 字段 resource_server_landing.provision_mode
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceServerProvisionModeEnum {

    SELF_DEPLOY(1, "自部署"),
    EXTERNAL(2, "第三方"),
    ;

    private final Integer code;
    private final String label;

    public static ResourceServerProvisionModeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (ResourceServerProvisionModeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public boolean matches(Integer code) { return this.code.equals(code); }
}
