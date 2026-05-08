package com.nook.biz.xray.controller.server.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 线路服务器一键安装入参。 */
@Data
public class LineServerInstallReqVO {

    @NotNull(message = "vmess 端口必填")
    @Min(value = 1, message = "vmess 端口最小 1")
    @Max(value = 65535, message = "vmess 端口最大 65535")
    private Integer vmessPort;

    @NotNull(message = "Xray gRPC 端口必填")
    @Min(value = 1, message = "Xray gRPC 端口最小 1")
    @Max(value = 65535, message = "Xray gRPC 端口最大 65535")
    private Integer xrayApiPort;

    @Size(max = 255)
    private String logDir;

    private Boolean installUfw;
    private Boolean enableBbr;
}
