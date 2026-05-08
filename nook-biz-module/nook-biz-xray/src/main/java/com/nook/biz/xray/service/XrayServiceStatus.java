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
    /** journalctl 最近 30 行 */
    private String log;

    public static XrayServiceStatus parse(String raw) {
        XrayServiceStatus s = new XrayServiceStatus();
        s.setActive(section(raw, "ACTIVE").trim());
        s.setVersion(section(raw, "VERSION").trim());
        s.setUptimeFrom(section(raw, "UPTIME").trim());
        s.setListening(section(raw, "LISTEN").trim());
        s.setLog(section(raw, "LOG"));
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
