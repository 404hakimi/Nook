package com.nook.biz.node.controller.resource.vo.landing;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - SOCKS5 落地节点装机事实 Response VO
 *
 * @author nook
 */
@Data
public class ServerLandingInstallRespVO {

    /** 服务器编号. */
    private String serverId;

    /** sockd -v 探测到的 dante 版本. */
    private String danteVersion;

    /** 安装根目录. */
    private String installDir;

    /** dante logoutput 路径. */
    private String logPath;

    /** sockd.conf 绝对路径. */
    private String confPath;

    /** PAM 配置文件路径. */
    private String pamFile;

    /** htpasswd 密码文件路径. */
    private String pwdFile;

    /** systemd 服务名. */
    private String systemdUnit;

    /** systemd 开机自启 1/0. */
    private Integer autostartEnabled;

    /** 装机时是否配过 UFW. */
    private Integer firewallEnabled;

    /** 是否配过 logrotate. */
    private Integer logRotateEnabled;

    /** 装机完成时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;

    /** 探测到的 dante 启动时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastDanteUptime;
}
