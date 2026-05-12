package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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

    /**
     * Xray 安装目录 (binary / config / share); 全部资源装在此目录下:
     *   <installDir>/bin/xray, <installDir>/etc/xray/config.json, <installDir>/share/xray/{geosite,geoip}.dat
     * 必须绝对路径; 不允许常用系统目录避免误操作 (校验见 isInstallDirSafe).
     */
    @NotBlank(message = "installDir 必填")
    @Pattern(regexp = "^/.+", message = "installDir 必须是绝对路径")
    @Size(max = 255)
    private String installDir;

    /** 远端 xray 日志目录; 留空时后端派生为 <installDir>/logs. */
    @Pattern(regexp = "^$|^/.+", message = "logDir 必须是绝对路径或留空")
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
