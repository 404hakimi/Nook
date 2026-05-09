package com.nook.biz.node.framework.xray.server.snapshot;

/**
 * Xray 进程的非 systemd 层信息; 与 SystemdStatusSnapshot 互补.
 *
 * @author nook
 */
public record XrayDaemonExtraSnapshot(
        /** Xray 二进制版本 (xray version 首行) */
        String version,
        /** 监听端口列表 (ss -ltn 过滤后的原文) */
        String listening
) {
}
