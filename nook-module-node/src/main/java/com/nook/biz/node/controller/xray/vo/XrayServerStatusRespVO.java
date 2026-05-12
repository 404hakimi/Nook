package com.nook.biz.node.controller.xray.vo;

import lombok.Data;

/** Xray systemd 服务运行状态 + xray 专属字段 (version / 监听端口); 由 XrayServerManageService.status 返回. */
@Data
public class XrayServerStatusRespVO {

    /** 查询的 systemd unit 名 (xray-managed 接口固定为 xray) */
    private String unit;
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
    /** systemctl is-enabled 输出: enabled / disabled / static / masked / ... — 表示是否开机自启. */
    private String enabled;
}
