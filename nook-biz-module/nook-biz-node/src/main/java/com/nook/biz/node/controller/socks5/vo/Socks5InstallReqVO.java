package com.nook.biz.node.controller.socks5.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SOCKS5 落地节点部署入参; SSH 凭据用完即弃, 不绑定 IP 池条目;
 * 字段范围由 Socks5InstallValidator 校验.
 *
 * @author nook
 */
@Data
public class Socks5InstallReqVO {

    @NotBlank(message = "sshHost 必填")
    private String sshHost;

    @NotNull(message = "sshPort 必填")
    private Integer sshPort;

    @NotBlank(message = "sshUser 必填")
    private String sshUser;

    @NotBlank(message = "sshPassword 必填")
    private String sshPassword;

    /** SSH 会话握手超时. */
    @NotNull(message = "sshTimeoutSeconds 必填")
    private Integer sshTimeoutSeconds;

    /** SSH 单条命令超时. */
    @NotNull(message = "sshOpTimeoutSeconds 必填")
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传超时. */
    @NotNull(message = "sshUploadTimeoutSeconds 必填")
    private Integer sshUploadTimeoutSeconds;

    /** 整体安装脚本最大耗时. */
    @NotNull(message = "installTimeoutSeconds 必填")
    private Integer installTimeoutSeconds;

    @NotNull(message = "socksPort 必填")
    private Integer socksPort;

    @NotBlank(message = "socksUser 必填")
    private String socksUser;

    @NotBlank(message = "socksPass 必填")
    private String socksPass;

    /** UFW allow_from 来源 CIDR; 留空 = 0.0.0.0/0; 推荐填中转线路服务器的公网 IP. */
    private String allowFrom;

    @NotNull(message = "installUfw 必填")
    private Boolean installUfw;
}
