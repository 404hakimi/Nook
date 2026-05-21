package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/** Agent 装机过程中后端固定的路径 + URL 常量; dialog 顶部 readonly 展示, 让 admin 知道脚本会动什么. */
@Data
public class AgentInstallMetaRespVO {

    /** 装机根目录 (所有 agent 文件都在此下). */
    private String nookHome;

    /** 远端 agent binary 绝对路径. */
    private String binPath;

    /** 远端 agent config.yml 绝对路径. */
    private String configPath;

    /** 远端 systemd unit 文件路径. */
    private String systemdUnitPath;

    /** 装机时 SSH 脚本里 curl 拉 binary 的完整 URL (含 role query). */
    private String binaryDownloadUrl;

    /** Backend 公网 URL (yaml 里 backend.api_url 用此值). */
    private String backendUrl;
}
