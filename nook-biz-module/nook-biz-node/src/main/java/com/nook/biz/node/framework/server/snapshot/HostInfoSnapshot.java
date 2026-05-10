package com.nook.biz.node.framework.server.snapshot;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 远端主机基本信息快照.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class HostInfoSnapshot {

    /** 主机名. */
    private String hostname;

    /** 内核版本 (uname -srm). */
    private String kernel;

    /** 发行版友好名 (/etc/os-release PRETTY_NAME). */
    private String osRelease;

    /** 系统已运行时长 (uptime -p). */
    private String systemUptime;

    /** 1/5/15 分钟平均负载. */
    private String loadAvg;

    /** 内存使用情况 (used / total + 百分比). */
    private String memory;

    /** 根分区使用情况 (used / total + 百分比). */
    private String disk;

    /** 时区 (timedatectl 优先, 兜底 date +%Z). */
    private String timezone;
}
