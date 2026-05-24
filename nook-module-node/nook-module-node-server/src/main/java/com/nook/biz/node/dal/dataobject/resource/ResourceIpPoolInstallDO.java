package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SOCKS5 装机事实 DO (跟 resource_ip_pool 1:1; 对标 xray_server)
 *
 * @author nook
 */
@Data
@TableName("resource_ip_pool_install")
public class ResourceIpPoolInstallDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "ip_id", type = IdType.INPUT)
    private String ipId;

    /** 装机时探测到的 dante 版本 (sockd -v). */
    private String danteVersion;

    /** 安装根目录 (logs / etc / info.txt 等运维资产放这里). */
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

    /** systemd 开机自启 (1=enable, 0=disable). */
    private Integer autostartEnabled;

    /** 装机时是否配过 UFW. */
    private Integer firewallEnabled;

    /** 是否配过 logrotate. */
    private Integer logRotateEnabled;

    /** 装机完成时间; 重装时覆写, 非首次语义. */
    private LocalDateTime installedAt;

    /** 探测到的 dante 启动时间; 跟 xray_server.lastXrayUptime 同语义. */
    private LocalDateTime lastDanteUptime;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
