package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerProvisionModeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 落地机扩展 DO
 *
 * @author nook
 */
@Data
@TableName("resource_server_landing")
public class ResourceServerLandingDO {

    /** 落地机 id (主键). */
    @TableId
    private String serverId;

    /** 部署模式 {@link ResourceServerProvisionModeEnum} */
    private Integer provisionMode;

    /** 落地 IP 类型. */
    private String ipTypeId;

    // ===== socks5 运行配置 =====

    /** SOCKS5 监听端口. */
    private Integer socks5Port;

    /** SOCKS5 认证用户名. */
    private String socks5Username;

    /** SOCKS5 认证密码 (明文). */
    private String socks5Password;

    /** dante log 关键字组合. */
    private String logLevel;

    /** dante 日志文件路径. */
    private String logPath;

    /** 是否随系统自启: 1=是 0=否. */
    private Integer autostartEnabled;

    /** 是否启用防火墙规则: 1=是 0=否. */
    private Integer firewallEnabled;

    /** 防火墙放行来源 IP/网段 (仅允许线路机入站). */
    private String firewallAllowFrom;

    /** dante 安装目录. */
    private String installDir;

    // ===== dante 装机事实 (provisionMode=1 自部署时填) =====

    /** 已装 dante 版本. */
    private String danteVersion;

    /** sockd.conf 绝对路径. */
    private String confPath;

    /** PAM 认证配置文件路径. */
    private String pamFile;

    /** SOCKS5 账号密码文件路径. */
    private String pwdFile;

    /** dante systemd unit 名. */
    private String systemdUnit;

    /** 是否启用日志轮转: 1=是 0=否. */
    private Integer logRotateEnabled;

    /** 装机完成时间. */
    private LocalDateTime installedAt;

    /** 上次探测到的 dante 启动时间. */
    private LocalDateTime lastDanteUptime;

    /** 创建时间. */
    private LocalDateTime createdAt;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
