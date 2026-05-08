package com.nook.biz.xray.service;

import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析后的 Xray service 状态。
 * 从 `xrayStatus()` 那条复合 shell 命令的输出中按段切出来。
 */
@Data
public class XrayServiceStatus {

    /** systemctl is-active 输出 (active / inactive / failed / unknown) */
    private String active;
    /** xray version 第一行, 如 "Xray 1.8.23 (Xray, ...)" */
    private String version;
    /** ActiveEnterTimestamp, 如 "Wed 2026-05-08 12:30:11 UTC" */
    private String uptimeFrom;
    /** 当前监听端口列表(从 ss 输出抓的 line 集合) */
    private String listening;
    /** journalctl 最近 N 行 */
    private String log;

    /** ===== 系统基本信息 (操作系统级别, 与 Xray 服务并列) ===== */
    /** hostname */
    private String hostname;
    /** uname -srm: kernel + arch */
    private String kernel;
    /** os-release PRETTY_NAME */
    private String osRelease;
    /** uptime -p: e.g. "up 3 days, 4 hours, 12 minutes" */
    private String systemUptime;
    /** load average 1min/5min/15min */
    private String loadAvg;
    /** free -m 提取 used/total MB, 与 used/total GB */
    private String memory;
    /** df -h / 提取 used/total */
    private String disk;
    /** date +%Z (时区) */
    private String timezone;

    public static XrayServiceStatus parse(String raw) {
        XrayServiceStatus s = new XrayServiceStatus();
        s.setActive(section(raw, "ACTIVE").trim());
        s.setVersion(section(raw, "VERSION").trim());
        s.setUptimeFrom(section(raw, "UPTIME").trim());
        s.setListening(section(raw, "LISTEN").trim());
        s.setLog(section(raw, "LOG"));
        s.setHostname(section(raw, "HOSTNAME").trim());
        s.setKernel(section(raw, "KERNEL").trim());
        s.setOsRelease(section(raw, "OS_RELEASE").trim());
        s.setSystemUptime(section(raw, "SYS_UPTIME").trim());
        s.setLoadAvg(section(raw, "LOADAVG").trim());
        s.setMemory(section(raw, "MEMORY").trim());
        s.setDisk(section(raw, "DISK").trim());
        s.setTimezone(section(raw, "TIMEZONE").trim());
        return s;
    }

    /**
     * 从形如 "====[NAME]====\n<内容>\n====[NEXT]====" 的输出里切一段。
     */
    private static String section(String raw, String name) {
        Pattern p = Pattern.compile("====\\[" + name + "\\]====\\R(.*?)(?=\\R====\\[|\\z)", Pattern.DOTALL);
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1) : "";
    }
}
