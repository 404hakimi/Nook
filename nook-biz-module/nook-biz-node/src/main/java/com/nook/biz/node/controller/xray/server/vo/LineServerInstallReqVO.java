package com.nook.biz.node.controller.xray.server.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Xray 线路服务器一键安装入参.
 *
 * @author nook
 */
@Data
public class LineServerInstallReqVO {

    /** 客户端口段起点; 1:1 模型下每客户独享 inbound 监听 slotPortBase + slotIndex (默认 30000). */
    @Min(value = 1024, message = "slotPortBase 最小 1024")
    @Max(value = 60000, message = "slotPortBase 最大 60000")
    private Integer slotPortBase;

    /** Slot 池大小; 该 server 最多承载客户数 (默认 50). */
    @Min(value = 1, message = "slotPoolSize 最小 1")
    @Max(value = 200, message = "slotPoolSize 最大 200")
    private Integer slotPoolSize;

    @NotNull(message = "Xray gRPC 端口必填")
    @Min(value = 1, message = "Xray gRPC 端口最小 1")
    @Max(value = 65535, message = "Xray gRPC 端口最大 65535")
    private Integer xrayApiPort;

    /** Xray 版本; null/"latest" 装最新 stable, 或 "v1.8.23" 这种具体版本. */
    @Size(max = 32)
    private String xrayVersion;

    @Size(max = 255)
    private String logDir;

    // ===== 可选模块勾选 =====

    /** 是否安装/启用 UFW 防火墙. */
    private Boolean installUfw;

    /** 是否启用 BBR 拥塞控制. */
    private Boolean enableBbr;

    /** 是否启用 swap (小内存机推荐). */
    private Boolean installSwap;

    /** swap 大小 MB, installSwap=true 时生效 (默认 1024). */
    @Min(value = 256, message = "swapSizeMb 最小 256")
    @Max(value = 8192, message = "swapSizeMb 最大 8192")
    private Integer swapSizeMb;

    /** IANA 时区, 如 Asia/Shanghai / UTC; 留空或 "skip" 则不改远端时区. */
    @Size(max = 64)
    private String timezone;
}
