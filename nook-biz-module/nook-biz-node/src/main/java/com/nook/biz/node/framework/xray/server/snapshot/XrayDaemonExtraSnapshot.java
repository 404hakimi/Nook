package com.nook.biz.node.framework.xray.server.snapshot;

/** Xray 进程的"非 systemd"层信息: 二进制版本 + 监听端口列表; 与 SystemdStatusSnapshot 互补. */
public record XrayDaemonExtraSnapshot(
        String version,
        String listening
) {
}
