package com.nook.biz.node.framework.server.snapshot;

/**
 * 远端主机基本信息快照.
 *
 * @author nook
 */
public record HostInfoSnapshot(
        /** 主机名 */
        String hostname,
        /** 内核版本 (uname -srm) */
        String kernel,
        /** 发行版友好名 (/etc/os-release PRETTY_NAME) */
        String osRelease,
        /** 系统已运行时长 (uptime -p) */
        String systemUptime,
        /** 1/5/15 分钟平均负载 */
        String loadAvg,
        /** 内存使用情况 (used / total + 百分比) */
        String memory,
        /** 根分区使用情况 (used / total + 百分比) */
        String disk,
        /** 时区 (timedatectl 优先, 兜底 date +%Z) */
        String timezone
) {
}
