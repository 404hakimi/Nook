package com.nook.biz.agent.controller.vo;

import lombok.Data;

/**
 * 管理后台 - Agent 装机元信息 Response VO
 *
 * @author nook
 */
@Data
public class AgentInstallMetaRespVO {

    /** Backend 公网 URL; 前端可改. */
    private String backendUrl;

    /** xray binary 绝对路径 (frontline + 选了 server 才填). */
    private String xrayBin;

    /** xray api server 端口 (loopback; frontline + 选了 server 才填). */
    private Integer xrayApiPort;

    /** SSH 连接超时秒数 (选了 server 才填). */
    private Integer sshTimeoutSeconds;

    /** SSH 命令执行超时秒数 (选了 server 才填). */
    private Integer sshOpTimeoutSeconds;

    /** SSH 上传超时秒数 (选了 server 才填). */
    private Integer sshUploadTimeoutSeconds;

    /** 装机超时秒数 (选了 server 才填). */
    private Integer installTimeoutSeconds;

    /** 落地机出网 IP (host 兜底 + admin 展示; landing + 选了 ipId 才填). */
    private String ipAddress;
}
