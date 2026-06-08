package com.nook.biz.node.controller.resource.vo.landing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点装机 Request VO
 *
 * @author nook
 */
@Data
public class ServerLandingDeployReqVO {

    /** SOCKS5 监听端口 (前端首次装机填 + 随机, 重装置灰; 装机时写回 landing 子表). */
    @NotNull(message = "socks5Port 不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer socks5Port;

    /** SOCKS5 用户. */
    @NotBlank(message = "socks5Username 不能为空")
    @Size(max = 64)
    private String socks5Username;

    /** SOCKS5 密码 (明文). */
    @NotBlank(message = "socks5Password 不能为空")
    @Size(max = 128)
    private String socks5Password;

    /** dante 日志关键字组合 (空格分隔). */
    @NotBlank(message = "logLevel 不能为空")
    @Size(max = 64)
    private String logLevel;

    /** dante logoutput 路径. */
    @NotBlank(message = "logPath 不能为空")
    @Size(max = 255)
    private String logPath;

    /** SOCKS5 安装目录. */
    @NotBlank(message = "installDir 不能为空")
    @Size(max = 255)
    private String installDir;

    /** sockd.conf 绝对路径. */
    @NotBlank(message = "confPath 不能为空")
    @Size(max = 255)
    private String confPath;

    /** PAM 配置文件路径. */
    @NotBlank(message = "pamFile 不能为空")
    @Size(max = 255)
    private String pamFile;

    /** htpasswd 密码文件路径. */
    @NotBlank(message = "pwdFile 不能为空")
    @Size(max = 255)
    private String pwdFile;

    /** systemd 服务名. */
    @NotBlank(message = "systemdUnit 不能为空")
    @Size(max = 64)
    private String systemdUnit;

    /** systemd 开机自启 (1/0). */
    @NotNull(message = "autostartEnabled 不能为空")
    @Min(value = 0) @Max(value = 1)
    private Integer autostartEnabled;

    /** 部署时是否配 UFW (1/0). */
    @NotNull(message = "firewallEnabled 不能为空")
    @Min(value = 0) @Max(value = 1)
    private Integer firewallEnabled;

    /** 装机时是否启用 logrotate (1/0). */
    @NotNull(message = "logRotateEnabled 不能为空")
    @Min(value = 0) @Max(value = 1)
    private Integer logRotateEnabled;
}
