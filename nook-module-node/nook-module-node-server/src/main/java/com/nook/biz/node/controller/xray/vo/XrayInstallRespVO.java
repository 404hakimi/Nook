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
public class XrayInstallRespVO {

    /** 服务器编号 (线路机). */
    private String serverId;

    /** 服务器别名. */
    private String serverName;

    /** 服务器主机. */
    private String serverHost;

    /** xray 版本号. */
    private String xrayVersion;

    /** xray gRPC API 端口. */
    private Integer xrayApiPort;

    /** xray 安装根目录. */
    private String xrayInstallDir;

    /** xray binary 绝对路径; 装机时落库. */
    private String xrayBinaryPath;

    /** xray config.json 绝对路径; 装机时落库. */
    private String xrayConfigPath;

    /** xray share 目录 (geo*.dat); 装机时落库. */
    private String xrayShareDir;

    /** xray 日志目录. */
    private String xrayLogDir;

    /** 远端 systemd unit 文件路径; 全节点固定常量, 后端回填. */
    private String xraySystemdUnitPath;

    /** 绑定的域名 system_domain.id; 空 = 未绑 / 不用 TLS. */
    private String domainId;

    /** 绑定的域名 (据 domainId 从 system_domain 回填; 装机时渲染进 xray_inbound). */
    private String domain;

    /** 装机完成时刻. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;

    /** xray 进程最近启动时刻. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastXrayUptime;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
