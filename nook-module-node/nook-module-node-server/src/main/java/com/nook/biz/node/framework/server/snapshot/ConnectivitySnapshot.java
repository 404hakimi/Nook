package com.nook.biz.node.framework.server.snapshot;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 主机可达性探活原始结果.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class ConnectivitySnapshot {

    /** 是否可达. */
    private boolean success;

    /** 探活耗时, 失败时为 0. */
    private long elapsedMs;

    /** 失败原因; 业务异常用 BusinessException.message, 其它走异常类名 + msg. */
    private String errorMessage;
}
