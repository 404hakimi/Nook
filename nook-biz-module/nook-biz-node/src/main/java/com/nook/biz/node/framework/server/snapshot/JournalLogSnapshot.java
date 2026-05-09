package com.nook.biz.node.framework.server.snapshot;

/**
 * journalctl 日志快照.
 *
 * @author nook
 */
public record JournalLogSnapshot(
        /** systemd unit 名 */
        String unit,
        /** 已 clamp 的实际行数 (默认 100, 上限 5000) */
        int lines,
        /** 归一化后的级别 (all / warning / err) */
        String level,
        /** journalctl 输出原文 */
        String log
) {
}
