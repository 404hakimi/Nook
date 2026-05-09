package com.nook.biz.node.framework.server.snapshot;

/**
 * 指定 systemd unit 的运行状态投影; 通用, 不掺任何 service 专属字段.
 *
 * @author nook
 */
public record SystemdStatusSnapshot(
        /** systemd unit 名 */
        String unit,
        /** is-active 输出 (active / inactive / failed / activating ...) */
        String active,
        /** 启动时间 (ActiveEnterTimestamp 原文) */
        String uptimeFrom,
        /** is-enabled 输出 (enabled / disabled / static / masked ...) */
        String enabled
) {
}
