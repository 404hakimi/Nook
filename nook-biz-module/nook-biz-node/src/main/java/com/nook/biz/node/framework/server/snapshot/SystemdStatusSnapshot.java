package com.nook.biz.node.framework.server.snapshot;

/** 指定 systemd unit 的运行状态投影 (active / 启动时间 / 是否开机自启); 通用, 不掺任何 Xray 专属字段. */
public record SystemdStatusSnapshot(
        String unit,
        String active,
        String uptimeFrom,
        String enabled
) {
}
