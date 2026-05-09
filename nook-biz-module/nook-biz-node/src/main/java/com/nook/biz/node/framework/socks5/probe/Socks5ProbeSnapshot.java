package com.nook.biz.node.framework.socks5.probe;

/**
 * SOCKS5 拨号探测原始结果.
 *
 * @author nook
 */
public record Socks5ProbeSnapshot(
        /** 拨号是否成功 */
        boolean success,
        /** 拨号耗时 */
        long elapsedMs,
        /** 出网真实 IP (echo-ip 端点返回); 仅 success=true 时有值 */
        String exitIp,
        /** 失败原因 (异常类名 + msg); 仅 success=false 时有值 */
        String errorMessage
) {
}
