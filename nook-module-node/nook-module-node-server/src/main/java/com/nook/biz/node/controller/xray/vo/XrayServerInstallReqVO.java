package com.nook.biz.node.controller.xray.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Xray 线路服务器一键安装入参.
 *
 * @author nook
 */
@Data
public class XrayServerInstallReqVO {

    /** 该 server 最多挂载的落地 IP 数量 (= 客户端数量上限). */
    @NotNull(message = "touchdownSize 必填")
    @Min(value = 1) @Max(value = 200)
    private Integer touchdownSize;

    /** xray 内置 api server 端口 (loopback). */
    @NotNull(message = "xrayApiPort 必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer xrayApiPort;

    /** Xray 版本; "latest" 装最新 stable, 或 "v26.3.27" 这种具体版本. */
    @NotBlank(message = "xrayVersion 必填")
    @Size(max = 32)
    private String xrayVersion;

    /**
     * Xray 安装根目录, 仅作展示分组用; 实际 binary / config / share / log / TLS 路径全部由前端独立传, 后端零派生.
     */
    @NotBlank(message = "installDir 必填")
    @Pattern(regexp = "^/.+", message = "installDir 必须是绝对路径")
    @Size(max = 255)
    private String installDir;

    /** xray binary 绝对路径; 前端默认 <installDir>/bin/xray, 落到 xray_node.xrayBinaryPath. */
    @NotBlank(message = "xrayBinaryPath 必填")
    @Pattern(regexp = "^/.+", message = "xrayBinaryPath 必须是绝对路径")
    @Size(max = 255)
    private String xrayBinaryPath;

    /** xray config.json 绝对路径; 前端默认 <installDir>/etc/xray/config.json. */
    @NotBlank(message = "xrayConfigPath 必填")
    @Pattern(regexp = "^/.+", message = "xrayConfigPath 必须是绝对路径")
    @Size(max = 255)
    private String xrayConfigPath;

    /** xray share 目录 (geo*.dat); 前端默认 <installDir>/share/xray. */
    @NotBlank(message = "xrayShareDir 必填")
    @Pattern(regexp = "^/.+", message = "xrayShareDir 必须是绝对路径")
    @Size(max = 255)
    private String xrayShareDir;

    /** 远端 xray 日志目录 (access.log / error.log 父目录); 前端默认 <installDir>/logs. */
    @NotBlank(message = "logDir 必填")
    @Pattern(regexp = "^/.+", message = "logDir 必须是绝对路径")
    @Size(max = 255)
    private String logDir;

    /** Xray 日志级别 (config.log.loglevel); debug/info/warning/error/none. */
    @NotBlank(message = "logLevel 必填")
    @Pattern(regexp = "^(debug|info|warning|error|none)$",
            message = "logLevel 必须是 debug/info/warning/error/none 之一")
    private String logLevel;

    /** systemd Restart= 策略; always/on-failure/no. */
    @NotBlank(message = "restartPolicy 必填")
    @Pattern(regexp = "^(always|on-failure|no)$",
            message = "restartPolicy 必须是 always/on-failure/no 之一")
    private String restartPolicy;

    /** 是否 systemctl enable xray (机器重启后自动起 xray). */
    @NotNull(message = "enableOnBoot 必填")
    private Boolean enableOnBoot;

    /** 强制重装; 即使已装版本与目标一致也走下载流程, 用于自编译版本 / build 后缀差异等场景. */
    @NotNull(message = "forceReinstall 必填")
    private Boolean forceReinstall;

    /** 是否安装 / 启用 UFW 防火墙. */
    @NotNull(message = "installUfw 必填")
    private Boolean installUfw;

    /** 是否设置远端时区; true = Asia/Shanghai, false = 跳过 (10-timezone 模块不渲染). */
    @NotNull(message = "setTimezone 必填")
    private Boolean setTimezone;

    /** 是否启用 logrotate 日志轮转; 推荐开启避免日志填满低配机磁盘. */
    @NotNull(message = "logRotate 必填")
    private Boolean logRotate;

    /** 共享 inbound 协议; 当前部署期固定 vmess (前端置灰), 协议适配阶段才放开. */
    @NotBlank(message = "protocol 必填")
    @Pattern(regexp = "vmess|vless|trojan", message = "protocol 必须是 vmess / vless / trojan 之一")
    @Size(max = 16)
    private String protocol;

    /** 共享 inbound 传输; 当前部署期固定 ws (前端置灰). */
    @NotBlank(message = "transport 必填")
    @Pattern(regexp = "tcp|ws|grpc|h2|quic", message = "transport 必须是 tcp / ws / grpc / h2 / quic 之一")
    @Size(max = 32)
    private String transport;

    /** 共享 inbound 监听 IP; 当前部署期固定 0.0.0.0 (前端置灰). */
    @NotBlank(message = "listenIp 必填")
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^::$|^[0-9a-fA-F:]+$",
            message = "listenIp 必须是合法 IPv4 / IPv6")
    @Size(max = 45)
    private String listenIp;

    /** 共享 inbound 监听端口; 默认 443. */
    @NotNull(message = "sharedInboundPort 必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer sharedInboundPort;

    /** WebSocket transport path. */
    @NotBlank(message = "wsPath 必填")
    @Pattern(regexp = "^/[A-Za-z0-9_\\-/]{0,127}$", message = "wsPath 必须以 / 开头, 仅字母数字_-/")
    @Size(max = 128)
    private String wsPath;

    /**
     * 是否走域名 + TLS (生产路径): true 时 domain / cfApiToken 必填, 安装链路会跑 CF A 记录 + 45-acme-tls 模块 + xray inbound 渲染 TLS 块; false 时这三项全部跳过, xray inbound 退化成纯 vmess+ws, 客户端走 IP:port 直连.
     */
    @NotNull(message = "useTls 必填")
    private Boolean useTls;

    /** 对外域名; useTls=true 时必填, useTls=false 时忽略. */
    @Pattern(regexp = "^$|^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$",
            message = "domain 格式非法")
    @Size(max = 255)
    private String domain;

    /**
     * Cloudflare API Token; useTls=true 且远端 cert 需新签时必填. 远端 ~/.acme.sh 保存供续期, 不入 nook DB.
     */
    @Size(max = 255)
    private String cfApiToken;

    /** TLS 证书路径; useTls=true 时必填, useTls=false 时前端可送空串. 前端默认 <installDir>/tls/cert.pem. */
    @Pattern(regexp = "^$|^/.+", message = "tlsCertPath 必须是绝对路径或空串")
    @Size(max = 255)
    private String tlsCertPath;

    /** TLS 私钥路径; useTls=true 时必填, useTls=false 时前端可送空串. 前端默认 <installDir>/tls/key.pem. */
    @Pattern(regexp = "^$|^/.+", message = "tlsKeyPath 必须是绝对路径或空串")
    @Size(max = 255)
    private String tlsKeyPath;
}
