package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
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

    @TableId
    private String serverId;

    // ===== 占用状态 =====

    /** 占用状态 {@link ResourceServerLandingStatusEnum} */
    private String status;
    private String occupiedByMemberId;
    private LocalDateTime occupiedAt;
    private LocalDateTime coolingUntil;
    private LocalDateTime reservedExpiresAt;
    private Integer assignCount;

    /** 部署模式 {@link ResourceServerProvisionModeEnum} */
    private Integer provisionMode;

    /** FK → system_ip_type.id. */
    private String ipTypeId;

    // ===== socks5 运行配置 =====

    private Integer socks5Port;
    private String socks5Username;
    private String socks5Password;

    /** dante log 关键字组合. */
    private String logLevel;
    private String logPath;
    private Integer autostartEnabled;
    private Integer firewallEnabled;
    private String firewallAllowFrom;
    private String installDir;

    // ===== dante 装机事实 =====

    private String danteVersion;

    /** sockd.conf 绝对路径. */
    private String confPath;
    private String pamFile;
    private String pwdFile;
    private String systemdUnit;
    private Integer logRotateEnabled;
    private LocalDateTime installedAt;
    private LocalDateTime lastDanteUptime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
