package com.nook.biz.node.framework.server.snapshot;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * journalctl 日志快照.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class JournalLogSnapshot {

    /** systemd unit 名. */
    private String unit;

    /** 已 clamp 的实际行数 (默认 100, 上限 5000). */
    private int lines;

    /** 归一化后的级别 (all / warning / err). */
    private String level;

    /** 关键词过滤 (子串匹配, 大小写不敏感); 空表示不过滤. */
    private String keyword;

    /** journalctl 输出原文. */
    private String log;
}
