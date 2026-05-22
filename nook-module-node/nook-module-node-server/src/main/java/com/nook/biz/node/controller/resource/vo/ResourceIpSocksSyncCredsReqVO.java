package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SOCKS5 凭据热同步入参; 只问 SSH 密码 (host/user 走 DB 值 + 缺省 root@22), SOCKS5 字段直接从 DB 读最新.
 *
 * <p>跟 install 不同: 不部署不装包, 仅推 dante 配置 + htpasswd 密码文件 + 视情况 restart.
 *
 * @author nook
 */
@Data
public class ResourceIpSocksSyncCredsReqVO {

    @NotBlank(message = "sshUser 必填")
    @Size(max = 64)
    private String sshUser;

    @NotBlank(message = "sshPassword 必填")
    @Size(max = 255)
    private String sshPassword;

    @NotNull(message = "sshPort 必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer sshPort;

    /** SSH 握手超时. */
    @NotNull
    @Min(value = 5) @Max(value = 600)
    private Integer sshTimeoutSeconds;

    /** 单条命令超时. */
    @NotNull
    @Min(value = 5) @Max(value = 300)
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传超时. */
    @NotNull
    @Min(value = 5) @Max(value = 600)
    private Integer sshUploadTimeoutSeconds;

    /** 整体脚本最大耗时; 凭据热更新预期 <30s, 给到 120s 兜底. */
    @NotNull
    @Min(value = 30) @Max(value = 600)
    private Integer installTimeoutSeconds;
}
