package com.nook.biz.node.controller.xray.server.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Xray 线路服务器一键安装入参; 字段范围 / 跨字段约束由 XrayServerInstallValidator 校验.
 *
 * @author nook
 */
@Data
public class LineServerInstallReqVO {

    /** 客户端口段起点; 1:1 模型每客户独享 inbound 监听 base + slotIndex. */
    @NotNull(message = "slotPortBase 必填")
    private Integer slotPortBase;

    /** Slot 池大小; 该 server 最多承载客户数. */
    @NotNull(message = "slotPoolSize 必填")
    private Integer slotPoolSize;

    /** xray 内置 api server 端口 (loopback). */
    @NotNull(message = "xrayApiPort 必填")
    private Integer xrayApiPort;

    /** Xray 版本; "latest" 装最新 stable, 或 "v1.8.23" 这种具体版本. */
    @NotBlank(message = "xrayVersion 必填")
    private String xrayVersion;

    /** 远端 xray 日志目录. */
    @NotBlank(message = "logDir 必填")
    private String logDir;

    /** 是否安装 / 启用 UFW 防火墙 (跟 slot 端口段联动, 留在 install 内). */
    @NotNull(message = "installUfw 必填")
    private Boolean installUfw;

    /** IANA 时区, 如 Asia/Shanghai / UTC; "skip" 表示不改远端时区. */
    @NotBlank(message = "timezone 必填 (skip 表示不改)")
    private String timezone;
}
