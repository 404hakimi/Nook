package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SOCKS5 落地节点部署入参; 装机成功后由 backend 事务内一次性落 5 子表 (主表 + cred + billing + socks5 + install + runtime).
 *
 * @author nook
 */
@Data
public class ResourceIpSocksInstallReqVO {

    // ===== 主表 (resource_ip_pool): 资源归属 =====

    @NotBlank(message = "region 必填")
    @Size(max = 32)
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, 如 JP-TYO / HK")
    private String region;

    @NotBlank(message = "ipTypeId 必填")
    @Size(max = 36)
    private String ipTypeId;

    @Size(max = 255)
    private String remark;

    // ===== SSH 凭据 (装机用 + 落 credential 子表) =====

    @NotBlank(message = "sshHost 必填")
    @Size(max = 128)
    private String sshHost;

    @NotNull(message = "sshPort 必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer sshPort;

    @NotBlank(message = "sshUser 必填")
    @Size(max = 64)
    private String sshUser;

    @NotBlank(message = "sshPassword 必填")
    @Size(max = 255)
    private String sshPassword;

    /** SSH 会话握手超时. */
    @NotNull(message = "sshTimeoutSeconds 必填")
    @Min(value = 5) @Max(value = 600)
    private Integer sshTimeoutSeconds;

    /** SSH 单条命令超时. */
    @NotNull(message = "sshOpTimeoutSeconds 必填")
    @Min(value = 5) @Max(value = 300)
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传超时. */
    @NotNull(message = "sshUploadTimeoutSeconds 必填")
    @Min(value = 5) @Max(value = 600)
    private Integer sshUploadTimeoutSeconds;

    /** 整体安装脚本最大耗时. */
    @NotNull(message = "installTimeoutSeconds 必填")
    @Min(value = 60) @Max(value = 3600)
    private Integer installTimeoutSeconds;

    // ===== dante 业务配置 (落 socks5 子表) =====

    @NotNull(message = "socksPort 必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer socksPort;

    @NotBlank(message = "socksUser 必填")
    @Size(max = 64)
    private String socksUser;

    @NotBlank(message = "socksPass 必填")
    @Size(max = 255)
    private String socksPass;

    @NotNull(message = "installUfw 必填")
    private Boolean installUfw;

    /** dante 日志关键字组合; 前端 default 'connect disconnect error'. */
    @NotBlank(message = "logLevel 必填")
    @Size(max = 64)
    private String logLevel;

    /** dante logoutput 路径; 前端 default 'installDir/logs/sockd.log'. */
    @NotBlank(message = "logPath 必填")
    @Size(max = 255)
    private String logPath;

    /** systemd 开机自启 (true=enable, false=disable). */
    @NotNull(message = "autostartEnabled 必填")
    private Boolean autostartEnabled;

    /** 是否启用 logrotate 日志轮转 (sockd.log 50M 触发 + gzip 压缩). */
    @NotNull(message = "logRotate 必填")
    private Boolean logRotate;

    /** SOCKS5 安装目录; 前端 default '/home/socks5'. */
    @NotBlank(message = "installDir 必填")
    @Size(max = 255)
    private String installDir;

    // ===== 装机产物路径 (落 install 子表; 前端 default; 后端只校验非空) =====

    /** sockd.conf 绝对路径; 前端 default '/etc/danted.conf'. */
    @NotBlank(message = "confPath 必填")
    @Size(max = 255)
    private String confPath;

    /** PAM 配置文件路径; 前端 default '/etc/pam.d/sockd'. */
    @NotBlank(message = "pamFile 必填")
    @Size(max = 255)
    private String pamFile;

    /** htpasswd 密码文件路径; 前端 default '/etc/danted/sockd.passwd'. */
    @NotBlank(message = "pwdFile 必填")
    @Size(max = 255)
    private String pwdFile;

    /** systemd 服务名; 前端 default 'danted'. */
    @NotBlank(message = "systemdUnit 必填")
    @Size(max = 64)
    private String systemdUnit;
}
