package com.nook.biz.xray.backend;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;

/** 服务器对接方式。 */
public enum XrayBackendType {

    /** 通过 3x-ui 面板 HTTP API 管理。 */
    THREEXUI("threexui"),
    /** 通过 Xray 内核原生 gRPC API 管理。 */
    XRAY_GRPC("xray-grpc");

    private final String code;

    XrayBackendType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 大小写/下划线/连字符宽容解析；找不到返回 null。 */
    public static XrayBackendType ofCode(String code) {
        if (StrUtil.isBlank(code)) return null;
        String normalized = code.trim().toLowerCase().replace('_', '-');
        return Arrays.stream(values())
                .filter(t -> t.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
