package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - SOCKS5 拨号测试结果 Response VO
 *
 * @author nook
 */
@Data
public class Socks5TestRespVO {

    private boolean success;

    /** 拨号 + HTTP 往返耗时毫秒. */
    private long elapsedMs;

    private String echoUrl;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;

    /** HTTP 响应状态码; success=true 时有值, 拨号失败时 0. */
    private int httpStatus;

    /** 响应体原文; success=true 时有值 (可能空串). */
    private String rawResponse;

    /** 拨号失败原因; success=false 时有值. */
    private String error;
}
