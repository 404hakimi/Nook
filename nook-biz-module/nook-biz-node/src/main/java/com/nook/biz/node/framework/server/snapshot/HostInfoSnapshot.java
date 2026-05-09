package com.nook.biz.node.framework.server.snapshot;

/** 远端主机基本信息: hostname/kernel/内存/磁盘/时区, 一次 SSH 复合命令拿全; 字段是 trim 后的 stdout 段. */
public record HostInfoSnapshot(
        String hostname,
        String kernel,
        String osRelease,
        String systemUptime,
        String loadAvg,
        String memory,
        String disk,
        String timezone
) {
}
