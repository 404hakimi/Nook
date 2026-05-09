package com.nook.biz.node.controller.socks5.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** SOCKS5 落地节点部署入参; SSH 凭据用完即弃, 不绑定 IP 池条目. */
@Data
public class Socks5InstallReqVO {

    // ===== 远端主机 SSH 凭据 (一次性) =====

    @NotBlank(message = "SSH 主机不能为空")
    @Size(max = 128)
    private String sshHost;

    @NotNull(message = "SSH 端口必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer sshPort;

    @NotBlank(message = "SSH 用户不能为空")
    @Size(max = 64)
    private String sshUser;

    /** sshPassword 与 sshPrivateKey 二选一 */
    @Size(max = 255)
    private String sshPassword;

    private String sshPrivateKey;

    @NotNull(message = "SSH 超时必填")
    @Min(value = 5) @Max(value = 600)
    private Integer sshTimeoutSeconds;

    // ===== SOCKS5 服务参数 =====

    @NotNull(message = "SOCKS5 端口必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer socksPort;

    @NotBlank(message = "SOCKS5 用户名不能为空")
    @Size(max = 64)
    private String socksUser;

    @NotBlank(message = "SOCKS5 密码不能为空")
    @Size(max = 255)
    private String socksPass;

    /** UFW allow from 来源 CIDR; 留空 = 0.0.0.0/0; 推荐填中转线路服务器的公网 IP */
    @Size(max = 255)
    private String allowFrom;

    /** 是否安装/启用 UFW */
    @NotNull(message = "installUfw 必填")
    private Boolean installUfw;
}
