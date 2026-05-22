package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Xray 节点 Resp VO
 *
 * @author nook
 */
@Data
public class XrayNodeRespVO {

    private String serverId;

    /** 服务器别名 (resource_server.name); 由 controller 批量回填; 已删 server 为空, 由前端 fallback 到 serverId. */
    private String serverName;

    /** 服务器主机 (resource_server.host); 同 serverName, 批量回填. */
    private String serverHost;

    private String xrayVersion;

    private Integer xrayApiPort;

    private String xrayLogDir;

    private String xrayInstallDir;

    /** xray binary 绝对路径; install 时落库, 前端展示用 (不再前端拼接). */
    private String xrayBinaryPath;

    /** xray config.json 绝对路径; install 时落库. */
    private String xrayConfigPath;

    /** xray share 目录 (geo*.dat); install 时落库. */
    private String xrayShareDir;

    /** 远端 systemd unit 文件路径; 全节点固定常量, 后端填, 前端只读. */
    private String xraySystemdUnitPath;

    /** 该 node 最多落地 IP 数量 (软上限). */
    private Integer touchdownSize;

    private Integer sharedInboundPort;

    private String wsPath;

    private String domain;

    private String tlsCertPath;

    private String tlsKeyPath;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastXrayUptime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
