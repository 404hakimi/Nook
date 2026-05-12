package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * SOCKS5 拨号测试出参; 后端只透传一次 HTTP 往返的结果, 不做业务解析, 由前端控制台展示.
 *
 * <p>success 反映"SOCKS5 → HTTP 往返是否完成", HTTP 4xx/5xx 也算 success=true; 前端按 httpStatus + body 自行判断.
 *
 * @author nook
 */
@Data
public class ResourceIpSocksTestRespVO {

    private boolean success;

    /** 拨号 + 拉到响应总耗时, 毫秒. */
    private long elapsedMs;

    /** 实际请求的 echo-IP 端点 URL; 即 req 入参原值, 失败也回填. */
    private String echoUrl;

    /** 实际使用的 TCP 建连超时, 毫秒. */
    private int connectTimeoutMs;

    /** 实际使用的 HTTP 读响应超时, 毫秒. */
    private int readTimeoutMs;

    /** HTTP 响应状态码; success=true 时有值, 拨号失败时 0. */
    private int httpStatus;

    /** echo 端点原始响应内容; success=true 时有值 (可能空串). */
    private String rawResponse;

    /** success=false 时的失败原因 (鉴权 / 超时 / 拒绝连接 等). */
    private String error;
}
