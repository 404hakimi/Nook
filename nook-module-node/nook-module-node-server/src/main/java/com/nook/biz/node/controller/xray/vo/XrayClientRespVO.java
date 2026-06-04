package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Xray 客户端 Response VO
 *
 * @author nook
 */
@Data
public class XrayClientRespVO {

    /** 客户端编号. */
    private String id;
    /** 服务器编号 (线路机). */
    private String serverId;
    /** 线路机别名 (服务器删除后为空). */
    private String serverName;
    /** 线路机主机地址. */
    private String serverHost;
    /** 落地节点编号. */
    private String ipId;
    /** 落地节点 IP (落地缺失时为空). */
    private String ipAddress;
    /** 所属会员. */
    private String memberUserId;
    /** 协议. */
    private String protocol;
    /** 传输方式. */
    private String transport;
    /** 监听 IP. */
    private String listenIp;
    /** 监听端口. */
    private Integer listenPort;
    /**
     * 协议密钥
     */
    private String clientUuid;
    /** 客户端标识 (email). */
    private String clientEmail;
    /** 客户端状态: 运行 / 已停 / 待同步 / 远端不存在. */
    private Integer status;

    /** 最近同步时刻. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncedAt;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
