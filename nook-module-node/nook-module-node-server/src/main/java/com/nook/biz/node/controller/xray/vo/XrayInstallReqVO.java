package com.nook.biz.node.controller.xray.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Xray 线路服务器一键安装入参; 基础设施 (端口/路径/日志/重启策略) 由后端 XrayInstallDefaults 固定, 前端只传版本 + 协议 + 域名 + 行为开关.
 *
 * @author nook
 */
@Data
public class XrayInstallReqVO {

    /** Xray 版本; "latest" 装最新 stable, 或 "v26.3.27" 这种具体版本. */
    @NotBlank(message = "xrayVersion 必填")
    @Size(max = 32)
    private String xrayVersion;

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

    /** 共享 inbound 协议; vmess (走 ws) 或 vless (走 reality). */
    @NotBlank(message = "protocol 必填")
    @Pattern(regexp = "vmess|vless|trojan", message = "protocol 必须是 vmess / vless / trojan 之一")
    @Size(max = 16)
    private String protocol;

    /** 共享 inbound 传输; vmess 走 ws, vless-reality 走 tcp. */
    @NotBlank(message = "transport 必填")
    @Pattern(regexp = "tcp|ws|grpc|h2|quic", message = "transport 必须是 tcp / ws / grpc / h2 / quic 之一")
    @Size(max = 32)
    private String transport;

    /** REALITY 偷取目标候选 (RealityDestPreset name); protocol=vless 时必填. */
    @Size(max = 32)
    private String realityDest;

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
     * 绑定的域名 system_domain.id (生产路径): 非空时安装链路跑 CF A 记录 + 45-acme-tls + xray inbound 渲染 TLS; 空时退化成纯 vmess+ws, 客户端走 IP:port 直连. domain / cfApiToken 由后端按此 id 从 system_domain 取.
     */
    @Size(max = 32)
    private String domainId;

    /** 二级域名标签 (如 frontline-jp-1); 绑定域名 (domainId 非空) 时必填, 完整 FQDN = subdomain + "." + 根域. */
    @Pattern(regexp = "^$|^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$",
            message = "subdomain 只能含字母数字与连字符 (可多级, 点分隔)")
    @Size(max = 128)
    private String subdomain;
}
