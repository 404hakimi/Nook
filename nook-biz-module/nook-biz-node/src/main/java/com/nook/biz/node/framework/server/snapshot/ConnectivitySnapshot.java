package com.nook.biz.node.framework.server.snapshot;

/**
 * 主机可达性探活原始结果.
 *
 * @author nook
 */
public record ConnectivitySnapshot(
        /** 是否可达 */
        boolean success,
        /** 探活耗时, 失败时为 0 */
        long elapsedMs,
        /** 失败原因; 业务异常用 BusinessException.message, 其它走异常类名 + msg */
        String errorMessage
) {
}
