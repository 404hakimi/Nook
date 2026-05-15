package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.enums.ResourceIpPoolStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IP 池条目 DO (一个出口 IP + SOCKS5 凭据).
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_ip_pool")
public class ResourceIpPoolDO extends BaseEntity {

    /** 区域 (us-west / jp / hk / sg 等) */
    private String region;

    /** 关联 resource_ip_type.id */
    private String ipTypeId;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum} */
    private Integer provisionMode;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址 */
    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;
    private String socks5Password;

    /** 状态; 取值见 {@link ResourceIpPoolStatusEnum} */
    private Integer status;

    private String assignedMemberId;
    private LocalDateTime assignedAt;

    private LocalDateTime coolingUntil;

    private Integer assignCount;
    private LocalDateTime lastHealthAt;

    /**
     * dante 日志关键字组合 (用空格分隔), 例: "connect disconnect error" / "error" / "connect disconnect iooperation".
     * 详见 danted.conf 的 log 选项 (connect/disconnect/data/error/iooperation/tcpinfo).
     */
    private String logLevel;

    /** dante logoutput 路径; 例 /var/log/sockd.log; 用 'syslog' 走 syslog. */
    private String logPath;

    /** systemd 开机自启 (1=enable 0=disable). */
    private Integer autostartEnabled;

    /** 部署时是否配 UFW (1=配置 0=跳过, 跳过等于完全开放). */
    private Integer firewallEnabled;

    /** UFW allow 来源 CIDR; NULL/空 = 0.0.0.0/0; 推荐填中转线路服务器公网 IP. */
    private String firewallAllowFrom;

    /**
     * SOCKS5 安装目录 (日志 + info 等运维资产), 默认 /home/socks5.
     * danted.conf 仍走 apt 标准路径 /etc/danted.conf 不动; install_dir 主要为 log_path 兜底 + 运维入口.
     */
    private String installDir;

    /** SSH 主机; 通常 = ipAddress, 留作独立列以防 NAT 等场景. */
    private String sshHost;

    /** SSH 端口 (默认 22). */
    private Integer sshPort;

    /** SSH 用户. */
    private String sshUser;

    /**
     * SSH 密码; 与 SOCKS5 密码同口径明文存储 (后台受信网络场景).
     * 部署成功后自动写入, 用于后续 详情 / 日志 / 切自启 等运维操作免输密码.
     */
    private String sshPassword;

    private String remark;
}
