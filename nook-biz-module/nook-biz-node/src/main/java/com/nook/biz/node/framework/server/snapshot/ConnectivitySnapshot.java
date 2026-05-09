package com.nook.biz.node.framework.server.snapshot;

/** 探活原始结果; success=false 时 errorMessage 是 BusinessException.message 或异常类名+msg. */
public record ConnectivitySnapshot(
        boolean success,
        long elapsedMs,
        String errorMessage
) {
}
