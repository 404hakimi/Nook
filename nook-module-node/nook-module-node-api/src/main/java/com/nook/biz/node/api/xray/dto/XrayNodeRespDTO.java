package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/** Xray 部署信息跨模块视图; 暴露 agent 装机 yaml 需要的两个字段 (bin / api_port). */
@Data
public class XrayNodeRespDTO {

    /** server 主键 (= ResourceServer.id). */
    private String serverId;

    /** Xray binary 绝对路径 (e.g., /home/xray/bin/xray). */
    private String xrayBinaryPath;

    /** Xray api server 监听端口 (loopback, agent statsquery 用). */
    private Integer xrayApiPort;
}
