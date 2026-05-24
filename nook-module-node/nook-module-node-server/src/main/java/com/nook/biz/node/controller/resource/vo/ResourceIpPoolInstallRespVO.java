package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - SOCKS5 装机事实 Response VO
 *
 * @author nook
 */
@Data
public class ResourceIpPoolInstallRespVO {

    private String ipId;
    private String danteVersion;
    private String installDir;
    private String logPath;
    private String confPath;
    private String pamFile;
    private String pwdFile;
    private String systemdUnit;
    private Integer autostartEnabled;
    private Integer firewallEnabled;
    private Integer logRotateEnabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastDanteUptime;
}
