package com.nook.biz.node.framework.socks5.probe;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SOCKS5 拨号探测原始结果.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class Socks5ProbeSnapshot {

    /** 拨号是否成功. */
    private boolean success;

    /** 拨号耗时. */
    private long elapsedMs;

    /** 出网真实 IP (echo-ip 端点返回); 仅 success=true 时有值. */
    private String exitIp;

    /** 失败原因 (异常类名 + msg); 仅 success=false 时有值. */
    private String errorMessage;
}
