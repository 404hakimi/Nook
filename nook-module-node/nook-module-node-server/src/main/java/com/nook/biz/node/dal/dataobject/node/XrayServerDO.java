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
 * Xray 实例元数据 DO (装机契约 / 部署事实, 跟 resource_server 1:1)
 *
 * @author nook
 */
@Data
@TableName("xray_server")
public class XrayServerDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务器 id (主键); FK → resource_server.id. */
    @TableId(value = "server_id", type = IdType.INPUT)
    private String serverId;

    /** 安装的 Xray 版本 */
    private String xrayVersion;

    /** Xray 内置 api server 端口 (loopback). */
    private Integer xrayApiPort;

    /** Xray 安装根目录 (binary / etc / share 都在此目录下). */
    private String xrayInstallDir;

    /** 远端 xray binary 绝对路径. */
    private String xrayBinaryPath;

    /** 远端 xray config.json 绝对路径. */
    private String xrayConfigPath;

    /** 远端 xray share 目录 (geo*.dat). */
    private String xrayShareDir;

    /** Xray 日志目录. */
    private String xrayLogDir;

    /** systemd unit 文件绝对路径; 装机时前端定义, 后端透传到 50-xray.sh.tmpl 写文件 + 入库. */
    private String xraySystemdUnitPath;

    /** 最近一次部署完成时间; 重装时也会覆写, 不是单纯的"首次"语义. */
    private LocalDateTime installedAt;

    /** 上次探测到的 xray 启动时间; reconciler 探测填, 重装时清零后重新探测. */
    private LocalDateTime lastXrayUptime;

    /** 创建时间. */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间. */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
