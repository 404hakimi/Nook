package com.nook.biz.node.framework.socks5.probe;

/** SOCKS5 拨号探测原始结果; success=false 时 errorMessage 是异常类名+msg, exitIp 仅 success=true 时有值. */
public record Socks5ProbeSnapshot(
        boolean success,
        long elapsedMs,
        String exitIp,
        String errorMessage
) {
}
