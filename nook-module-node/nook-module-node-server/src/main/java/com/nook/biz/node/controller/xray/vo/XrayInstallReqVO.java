package com.nook.biz.node.controller.xray.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Xray 线路服务器一键安装入参; 基础设施 (端口/路径/日志/重启) 由后端 XrayInstallDefaults 固定, 前端传版本 + 行为开关 + inbound 配置.
 *
 * @author nook
 */
@Data
public class XrayInstallReqVO {

    /** Xray 版本; "latest" 装最新 stable, 或 "v26.3.27" 这种具体版本. */
    @NotBlank(message = "xrayVersion 必填")
    @Size(max = 32)
    private String xrayVersion;

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

    /** 共享 inbound 配置 (协议形态 + 监听 + 协议特定参数 ws/reality/域名). */
    @Valid
    @NotNull(message = "inbound 必填")
    private XrayInboundConfigVO inbound;
}
