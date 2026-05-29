package com.nook.biz.node.dal.dataobject.node;

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
 * Xray inbound 共享配置 DO (业务可热改, 跟 resource_server 1:1)
 *
 * @author nook
 */
@Data
@TableName("xray_config")
public class XrayConfigDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务器 id (主键); FK → resource_server.id. */
    @TableId(value = "server_id", type = IdType.INPUT)
    private String serverId;

    /** 共享 inbound 协议 (vmess/trojan/...) */
    private String protocol;

    /** 共享 inbound 传输 (ws/tcp/...) */
    private String transport;

    /** 共享 inbound 监听 IP. */
    private String listenIp;

    /** 共享 inbound 监听端口. */
    private Integer sharedInboundPort;

    /** WebSocket transport path (CDN 接入). */
    private String wsPath;

    /** 对外域名 (CDN CNAME 指向). */
    private String domain;

    /** TLS 证书路径 (acme.sh 签发后填). */
    private String tlsCertPath;

    /** TLS 私钥路径 (acme.sh 签发后填). */
    private String tlsKeyPath;

    /** 创建时间. */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间. */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
