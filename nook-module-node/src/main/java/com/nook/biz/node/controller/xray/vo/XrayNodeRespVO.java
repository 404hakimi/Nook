package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Xray 节点 Resp VO
 *
 * @author nook
 */
@Data
public class XrayNodeRespVO {

    private String serverId;

    /** 服务器别名 (resource_server.name); 由 controller 批量回填; 已删 server 为空, 由前端 fallback 到 serverId. */
    private String serverName;

    /** 服务器主机 (resource_server.host); 同 serverName, 批量回填. */
    private String serverHost;

    private String xrayVersion;

    private Integer xrayApiPort;

    private String xrayLogDir;

    private String xrayInstallDir;

    private Integer slotPoolSize;

    private Integer slotPortBase;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastXrayUptime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime installedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
