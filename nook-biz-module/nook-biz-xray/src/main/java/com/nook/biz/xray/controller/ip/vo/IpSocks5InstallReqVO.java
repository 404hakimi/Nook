package com.nook.biz.xray.controller.ip.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * IP 池一键部署 SOCKS5 入参。
 * SSH 凭据: 临时使用, 不持久化(IP 池条目本身不存 SSH 凭据);
 * 部署成功后 SOCKS5 端口/用户/密码 会回写到 resource_ip_pool 表。
 */
@Data
public class IpSocks5InstallReqVO {

    // ===== 远端 SOCKS5 主机 SSH 凭据(只用一次, 不存) =====

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

    /** SSH 命令最大耗时秒; 留空走 SshExecutor 内部默认 */
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

    /** UFW allow from 来源 CIDR, 留空 = 0.0.0.0/0; 推荐填中转线路服务器的公网 IP */
    @Size(max = 255)
    private String allowFrom;

    /** 是否安装/启用 UFW */
    private Boolean installUfw;
}
