package com.nook.biz.node.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * IP 池条目状态枚举.
 *
 * <p>状态机: available → occupied → cooling → available (冷却到期后由调度器 sweep 回收).
 * 人工干预: testing / blacklisted / degraded.
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum ResourceIpPoolStatusEnum {

    AVAILABLE(1, "可分配"),
    OCCUPIED(2, "已占用"),
    TESTING(3, "测试中"),
    BLACKLISTED(4, "拉黑"),
    COOLING(5, "冷却中"),
    DEGRADED(6, "降级"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(ResourceIpPoolStatusEnum::getStatus).toArray(Integer[]::new);

    public static final Integer MIN_VALUE = AVAILABLE.status;
    public static final Integer MAX_VALUE = DEGRADED.status;

    private final Integer status;
    private final String label;

    public static ResourceIpPoolStatusEnum fromStatus(Integer status) {
        if (status == null) return null;
        for (ResourceIpPoolStatusEnum e : values()) {
            if (e.status.equals(status)) return e;
        }
        return null;
    }

    public boolean matches(Integer status) {
        return this.status.equals(status);
    }
}
