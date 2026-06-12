package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/**
 * Xray 实例元数据 Response DTO
 *
 * @author nook
 */
@Data
public class XrayInstallRespDTO {

    /** 服务器ID. */
    private String serverId;

    /** Xray binary 绝对路径 (e.g., /home/xray/bin/xray). */
    private String xrayBinaryPath;

    /** Xray api server 监听端口 (loopback, agent statsquery 用). */
    private Integer xrayApiPort;

    /** Xray 版本号 (e.g., v26.3.27); null = 未上报. */
    private String xrayVersion;
}
