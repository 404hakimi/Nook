package com.nook.biz.node.controller.xray.server.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    /** 客户端口段起点; 1:1 模型每客户独享 inbound 监听 base + slotIndex. */
    @NotNull(message = "slotPortBase 必填")
    @Min(value = 1024) @Max(value = 60000)
    private Integer slotPortBase;

    /** Slot 池大小; 该 server 最多承载客户数. */
    @NotNull(message = "slotPoolSize 必填")
    @Min(value = 1) @Max(value = 200)
    private Integer slotPoolSize;

    /** xray 内置 api server 端口 (loopback). */
    @NotNull(message = "xrayApiPort 必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer xrayApiPort;

    /** Xray 版本; "latest" 装最新 stable, 或 "v26.3.27" 这种具体版本. */
    @NotBlank(message = "xrayVersion 必填")
    @Size(max = 32)
    private String xrayVersion;

    /** 远端 xray 日志目录. */
    @NotBlank(message = "logDir 必填")
    @Size(max = 255)
    private String logDir;

    /** 是否安装 / 启用 UFW 防火墙 (跟 slot 端口段联动, 留在 install 内). */
    @NotNull(message = "installUfw 必填")
    private Boolean installUfw;

    /** IANA 时区, 如 Asia/Shanghai / UTC; "skip" 表示不改远端时区. */
    @NotBlank(message = "timezone 必填 (skip 表示不改)")
    @Size(max = 64)
    private String timezone;

    /**
     * 跨字段校验: xray api 端口不能落在 slot 端口段内, 否则启动时端口冲突.
     * 任一依赖字段为 null 时跳过, 由 @NotNull 单独报错.
     */
    @AssertTrue(message = "xrayApiPort 不能落在 slot 端口段 [slotPortBase, slotPortBase + slotPoolSize] 内")
    @JsonIgnore
    public boolean isXrayApiPortNotConflictSlotRange() {
        if (xrayApiPort == null || slotPortBase == null || slotPoolSize == null) {
            return true;
        }
        int slotEnd = slotPortBase + slotPoolSize;
        return xrayApiPort < slotPortBase || xrayApiPort > slotEnd;
    }
}
