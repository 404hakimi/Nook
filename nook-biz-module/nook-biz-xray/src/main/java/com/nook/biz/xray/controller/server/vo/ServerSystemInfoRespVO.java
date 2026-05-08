package com.nook.biz.xray.controller.server.vo;

import lombok.Data;

/**
 * 远端服务器操作系统级别基本信息; 与 Xray 服务并列的"主机视角"快照, 不依赖 Xray 是否在跑。
 * 字段值都是 SSH 命令原样输出, 前端按需展示。
 */
@Data
public class ServerSystemInfoRespVO {

    /** hostname */
    private String hostname;
    /** uname -srm: kernel + arch */
    private String kernel;
    /** /etc/os-release PRETTY_NAME, 如 "Ubuntu 22.04.4 LTS" */
    private String osRelease;
    /** uptime -p, 如 "up 3 days, 4 hours, 12 minutes" */
    private String systemUptime;
    /** /proc/loadavg 前三个数, 1/5/15min 平均负载 */
    private String loadAvg;
    /** 内存使用, 形如 "2.1G / 7.7G (28%)" */
    private String memory;
    /** 根分区使用, 形如 "12G / 50G (25%)" */
    private String disk;
    /** timedatectl Timezone, 如 "Asia/Shanghai" */
    private String timezone;
}
