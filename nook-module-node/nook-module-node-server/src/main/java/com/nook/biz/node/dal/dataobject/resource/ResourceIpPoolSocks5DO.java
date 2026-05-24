package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 池 dante 业务配置 DO (1:1, agent 改 sockd.conf 用)
 *
 * <p>装机产物 (install_dir / log_path / autostart / firewall) 拆到 resource_ip_pool_install 子表.
 * <p>实际限速 (bandwidth_limit_mbps) 和月流量上限拆到 resource_ip_pool_capacity 子表.
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
