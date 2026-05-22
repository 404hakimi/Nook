package com.nook.biz.node.framework.socks5.probe;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SOCKS5 拨号探测原始结果.
 *
 * <p>success 仅表示"是否完成一次 SOCKS5 → HTTP 往返", HTTP 任何状态码 (200 / 4xx / 5xx) 都算成功;
 * 前端拿到 rawResponse 自行决定是不是"业务上符合预期".
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

    /** 实际请求的 echo-IP 端点 URL; 永远回填便于审计 / 排查. */
    private String echoUrl;

    /** 实际使用的 TCP 建连超时, 毫秒; 永远回填. */
    private int connectTimeoutMs;

    /** 实际使用的 HTTP 读响应超时, 毫秒; 永远回填. */
    private int readTimeoutMs;

    /** HTTP 响应状态码; success=true 时有值, 拨号失败时 0. */
    private int httpStatus;

    /** echo-IP 端点的原始响应文本; 仅 success=true 时有值 (可能是空串, 但不会是 null). */
    private String rawResponse;

    /** 失败原因 (异常类名 + msg); 仅 success=false 时有值. */
    private String errorMessage;
}
