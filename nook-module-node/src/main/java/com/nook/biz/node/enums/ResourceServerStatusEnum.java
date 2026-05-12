package com.nook.biz.node.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 服务器状态 枚举
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceServerStatusEnum {

    RUNNING(1, "运行"),
    MAINTENANCE(2, "维护"),
    OFFLINE(3, "下线"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(ResourceServerStatusEnum::getStatus).toArray(Integer[]::new);

    public static final Integer MIN_VALUE = RUNNING.status;
    public static final Integer MAX_VALUE = OFFLINE.status;

    private final Integer status;
    private final String label;

    public static ResourceServerStatusEnum fromStatus(Integer status) {
        if (status == null) return null;
        for (ResourceServerStatusEnum e : values()) {
            if (e.status.equals(status)) return e;
        }
        return null;
    }

    public boolean matches(Integer status) {
        return this.status.equals(status);
    }
}
