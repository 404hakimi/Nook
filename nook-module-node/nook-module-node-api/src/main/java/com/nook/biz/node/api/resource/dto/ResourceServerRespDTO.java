package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/** ResourceServer 跨模块视图; 暴露 agent 装机 / 心跳 / SSH 连接需要的字段 (不含产品 / 计费 / CF 这类无关字段). */
@Data
public class ResourceServerRespDTO {

    /** server 主键. */
    private String id;

    /** server 名 (展示用). */
    private String name;

    /** SSH 主机 IP / 域名. */
    private String host;

    /** SSH 端口. */
    private Integer sshPort;

    /** SSH 用户名. */
    private String sshUser;

    /** SSH 密码; agent 装机时建 SessionCredential 用. */
    private String sshPassword;

    /** SSH 握手超时 (秒). */
    private Integer sshTimeoutSeconds;

    /** SSH 单条命令超时 (秒). */
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传超时 (秒). */
    private Integer sshUploadTimeoutSeconds;

    /** 整段安装脚本超时 (秒). */
    private Integer installTimeoutSeconds;

    /** Server lifecycle 状态 (INSTALLING / READY / LIVE / RETIRED). */
    private String lifecycleState;

    /** Agent 鉴权 token; createServer 时一次性签发. */
    private String agentToken;
}
