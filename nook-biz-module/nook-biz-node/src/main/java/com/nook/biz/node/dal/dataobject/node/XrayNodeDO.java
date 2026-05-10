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
 * Xray 节点表 (server 上跑的 xray 实例配置 + 状态), 与 resource_server 1:1 关联.
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

    /** 关联 resource_server.id, 1:1 主键. */
    @TableId(value = "server_id", type = IdType.INPUT)
    private String serverId;

    /** 安装的 Xray 版本 (如 v1.8.23). */
    private String xrayVersion;

    /** Xray 内置 api server 端口 (loopback); 走 SSH 远端 `xray api -s 127.0.0.1:port adi/rmi` CLI. */
    private Integer xrayApiPort;

    /** Xray 日志目录. */
    private String xrayLogDir;

    /** Slot 池大小, 该 node 最多承载客户数. */
    private Integer slotPoolSize;

    /** Slot 端口段起点; slot_index=1 → port=slot_port_base+1. */
    private Integer slotPortBase;

    /** 上次探测到的 xray 启动时间, 用于判断是否需 replay. */
    private LocalDateTime lastXrayUptime;

    /** Xray 首次安装完成时间. */
    private LocalDateTime installedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
