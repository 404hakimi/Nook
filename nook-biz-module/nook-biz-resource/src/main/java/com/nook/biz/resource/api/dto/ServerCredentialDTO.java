package com.nook.biz.resource.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 跨模块 DTO: 一台服务器的 SSH 接入凭据; resource 模块从 resource_server 表读出, 其他模块用它跑 SSH 命令.
 *
 * @author nook
 */
@Data
@Builder
public class ServerCredentialDTO {

    private String serverId;

    private String sshHost;
    private int sshPort;
    private String sshUser;
    private String sshPassword;

    /** SSH 会话握手超时(秒). */
    private int sshTimeoutSeconds;

    /** SSH 单条命令最大耗时(秒); xray api / systemctl / journalctl 共用. */
    private int sshOpTimeoutSeconds;

    /** SCP 上传单文件超时(秒); 上传脚本 / 模板用. */
    private int sshUploadTimeoutSeconds;

    /** 一次安装脚本最大耗时(秒); install 全量脚本 + Emitter 端 + 60s. */
    private int installTimeoutSeconds;
}
