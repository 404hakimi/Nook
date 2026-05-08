package com.nook.biz.xray.controller.server.vo;

import lombok.Data;

@Data
public class ConnectivityTestRespVO {

    private boolean success;

    /** 探活耗时毫秒，仅 success=true 时有意义 */
    private long elapsedMs;

    private String backendType;

    /** 失败时填错误描述；成功留空 */
    private String error;
}
