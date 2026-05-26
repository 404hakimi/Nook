package com.nook.biz.node.framework.socks5.probe;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SOCKS5 拨号探测结果
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class Socks5ProbeSnapshot {

    /** SOCKS5 → HTTP 往返是否完成; false = 拨号 / 握手 / IO 异常. */
    private boolean success;

    /** 拨号耗时. */
    private long elapsedMs;

    /** 实际请求的 echo-IP 端点 URL. */
    private String echoUrl;

    /** 实际使用的 TCP 建连超时, 毫秒. */
    private int connectTimeoutMs;

    /** 实际使用的 HTTP 读响应超时, 毫秒. */
    private int readTimeoutMs;

    /** HTTP 响应状态码; success=true 时有值, 拨号失败时 0. */
    private int httpStatus;

    /** echo-IP 端点的原始响应文本; 仅 success=true 时有值. */
    private String rawResponse;

    /** 失败原因; 仅 success=false 时有值. */
    private String errorMessage;
}
