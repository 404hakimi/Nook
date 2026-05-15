package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.node.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.enums.ResourceIpPoolStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 池列表 / 详情 Response VO.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolRespVO {

    private String id;
    private String region;
    private String ipTypeId;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum} */
    private Integer provisionMode;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址 */
    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;

    /** 明文 SOCKS5 密码; 后台运营受信网络使用, UI 用 type=password 自然遮盖. */
    private String socks5Password;

    /** 状态; 取值见 {@link ResourceIpPoolStatusEnum} */
    private Integer status;

    private String assignedMemberId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime coolingUntil;

    private Integer assignCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHealthAt;

    /** dante 日志关键字组合 (空格分隔); 例 'connect disconnect error'. */
    private String logLevel;

    /** dante logoutput 路径. */
    private String logPath;

    /** systemd 开机自启 (1/0). */
    private Integer autostartEnabled;

    /** 部署时是否配 UFW (1/0). */
    private Integer firewallEnabled;

    /** UFW allow 来源 CIDR; NULL = 0.0.0.0/0. */
    private String firewallAllowFrom;

    /** SOCKS5 安装目录; 默认 /home/socks5. */
    private String installDir;

    /** SSH 主机; 留空时业务侧用 ipAddress 兜底. */
    private String sshHost;

    /** SSH 端口 (默认 22). */
    private Integer sshPort;

    /** SSH 用户. */
    private String sshUser;

    /** 明文 SSH 密码; 后台受信网络场景, 跟 SOCKS5 密码同口径下发. UI 用 type=password 自然遮盖. */
    private String sshPassword;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
