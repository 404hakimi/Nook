package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 池 dante 配置 + 限速 DO (1:1, agent 改 sockd.conf 用)
 *
 * @author nook
 */
@Data
@TableName("resource_ip_pool_socks5")
public class ResourceIpPoolSocks5DO {

    @TableId
    private String ipId;

    private Integer socks5Port;

    /** 明文 (待加密). */
    private String socks5Username;

    /** 明文 (待加密). */
    private String socks5Password;

    /** dante log 关键字组合 (空格分隔). */
    private String logLevel;

    /** dante logoutput 路径; NULL=install_dir/logs/sockd.log 兜底. */
    private String logPath;

    /** systemd 开机自启 (1=enable, 0=disable). */
    private Integer autostartEnabled;

    /** 部署时是否配 UFW (1/0). */
    private Integer firewallEnabled;

    /** dante 安装目录; danted.conf 仍在 /etc/danted.conf. */
    private String installDir;

    /** dante 实际限速 Mbps; 0=不限; agent 改 sockd.conf 落实. */
    private Integer bandwidthLimitMbps;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
