package com.nook.biz.node.framework.server.snapshot;

/** journalctl -u {unit} 输出快照; lines 是已 clamp 的实际值, level 已规范化为 all/warning/err. */
public record JournalLogSnapshot(
        String unit,
        int lines,
        String level,
        String log
) {
}
