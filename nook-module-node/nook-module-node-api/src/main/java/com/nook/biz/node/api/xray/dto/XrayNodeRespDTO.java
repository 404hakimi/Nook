package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/**
 * Xray 节点 Response DTO
 *
 * @author nook
 */
@Data
public class XrayNodeRespDTO {

    /** server 主键 (= ResourceServer.id). */
    private String serverId;

    /** Xray binary 绝对路径 (e.g., /home/xray/bin/xray). */
    private String xrayBinaryPath;

    /** Xray api server 监听端口 (loopback, agent statsquery 用). */
    private Integer xrayApiPort;
}
