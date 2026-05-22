package com.nook.biz.node.dal.dataobject.node;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Xray 节点表 (server 上跑的 xray 实例配置 + 状态), 与 resource_server 一一对应.
 *
 * <p>resource_server 只管"机器活着 + SSH 通", 任何 xray 相关字段都放这里.
 * 同一台 server 没装 xray 时本表无对应行, 装了之后写入一行, 后续配置 / 状态都在这.
 *
 * @author nook
 */
@Data
@TableName("xray_node")
public class XrayNodeDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务器ID */
    @TableId(value = "server_id", type = IdType.INPUT)
    private String serverId;

    /** 安装的 Xray 版本 */
    private String xrayVersion;

    /** Xray 内置 api server 端口 (loopback); 走 SSH 远端 `xray api -s 127.0.0.1:port adi/rmi` CLI. */
    private Integer xrayApiPort;

    /** 共享 inbound 协议; 当前部署期固定 vmess, 协议适配阶段才放开可改. */
    private String protocol;

    /** 共享 inbound 传输; 当前部署期固定 ws. */
    private String transport;

    /** 共享 inbound 监听 IP; 当前部署期固定 0.0.0.0. */
    private String listenIp;

    /** 共享 inbound 监听端口 (默认 443). */
    private Integer sharedInboundPort;

    /** WebSocket transport path (CDN 接入). */
    private String wsPath;

    /** 对外域名 (CDN CNAME 指向). */
    private String domain;

    /** TLS 证书路径 (acme.sh 签发后填). */
    private String tlsCertPath;

    private String tlsKeyPath;

    /** Xray 日志目录. */
    private String xrayLogDir;

    /** Xray 安装根目录 (binary / etc / share 都在此目录下); 与 reqVO.installDir 一致. */
    private String xrayInstallDir;

    /** 远端 xray binary 绝对路径; install 时由脚本布局决定 ($INSTALL_DIR/bin/xray). */
    private String xrayBinaryPath;

    /** 远端 xray config.json 绝对路径 ($INSTALL_DIR/etc/xray/config.json). */
    private String xrayConfigPath;

    /** 远端 xray share 目录 (geo*.dat 数据, $INSTALL_DIR/share/xray). */
    private String xrayShareDir;

    /** 该 node 最多挂载的落地 IP 数量 (= 客户端数量上限, 软上限; provision 时 count(active client) < touchdownSize 才允许新开通). */
    private Integer touchdownSize;

    /** 上次探测到的 xray 启动时间, 用于判断是否需 replay; 重装时清零等 reconciler 重新探测填. */
    private LocalDateTime lastXrayUptime;

    /** 最近一次部署完成时间; 重装时也会覆写, 不是单纯的"首次"语义. */
    private LocalDateTime installedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
