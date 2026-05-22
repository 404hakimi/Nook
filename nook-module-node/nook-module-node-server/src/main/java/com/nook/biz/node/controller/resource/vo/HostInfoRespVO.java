package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 服务器主机基本信息 (从 ServerProbe.readHostInfo 拿到的 8 字段平铺投影).
 * 给 Xray / SOCKS5 状态弹框的"主机信息"折叠区共用; 不绑特定 service.
 *
 * @author nook
 */
@Data
public class HostInfoRespVO {

    private String hostname;

    /** uname -srm */
    private String kernel;

    /** /etc/os-release 的 PRETTY_NAME */
    private String osRelease;

    /** uptime -p 输出, 如 "up 5 days, 3 hours" */
    private String systemUptime;

    /** /proc/loadavg 前 3 个值, 1/5/15 分钟负载 */
    private String loadAvg;

    /** "used / total (pct%)" 风格的内存占用 */
    private String memory;

    /** 根分区 "used / total (pct%)" 风格的磁盘占用 */
    private String disk;

    /** IANA 时区, 如 Asia/Shanghai */
    private String timezone;
}
