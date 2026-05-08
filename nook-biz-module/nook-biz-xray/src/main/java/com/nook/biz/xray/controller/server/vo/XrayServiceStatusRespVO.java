package com.nook.biz.xray.controller.server.vo;

import lombok.Data;

/**
 * Xray systemd 服务运行状态; 不含日志 (日志走单独的 log 接口避免单次返回过大)。
 */
@Data
public class XrayServiceStatusRespVO {

    /** systemctl is-active 输出: active / inactive / failed / unknown */
    private String active;
    /** xray version 第一行, 如 "Xray 1.8.23 (Xray, ...)" */
    private String version;
    /** ActiveEnterTimestamp, 如 "Wed 2026-05-08 12:30:11 UTC"; 服务未起时为空 */
    private String uptimeFrom;
    /**
     * 监听端口列表 (ss -ltn 抓取相关行); 当前会过滤 127.0.0.1:gRPC、:443、:2087 等
     * nook 关心的端口, 多行字符串, 前端按 \n 拆分展示。
     */
    private String listening;
}
