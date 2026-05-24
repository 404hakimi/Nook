package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Xray 实例元数据 Resp VO (装机契约 / 部署事实)
 *
 * @author nook
 */
@Data
public class XrayServerRespVO {

    private String serverId;

    /** 服务器别名 (resource_server.name); 由 controller 批量回填. */
    private String serverName;

    /** 服务器主机 (resource_server_credential.host); 由 controller 批量回填. */
    private String serverHost;

    private String xrayVersion;

    private Integer xrayApiPort;

    private String xrayInstallDir;

    /** xray binary 绝对路径; 装机时落库. */
    private String xrayBinaryPath;

    /** xray config.json 绝对路径; 装机时落库. */
    private String xrayConfigPath;

    /** xray share 目录 (geo*.dat); 装机时落库. */
    private String xrayShareDir;

    private String xrayLogDir;

    /** 远端 systemd unit 文件路径; 全节点固定常量, 后端回填. */
    private String xraySystemdUnitPath;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastXrayUptime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
