package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 管理后台 - SOCKS5 落地节点创建 Request VO
 *
 * @author nook
 */
@Data
public class ServerLandingCreateReqVO {

    // ===== 主表核心 =====

    @NotBlank(message = "区域不能为空")
    @Size(max = 32, message = "区域码长度不能超过 32")
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    @NotBlank(message = "IP 类型不能为空")
    private String ipTypeId;

    @Pattern(regexp = "INSTALLING|READY|LIVE|RETIRED", message = "lifecycleState 须为 INSTALLING/READY/LIVE/RETIRED")
    private String lifecycleState;

    @NotBlank(message = "ipAddress 不能为空")
    @Size(max = 64)
    private String ipAddress;

    @Size(max = 512)
    private String remark;

    // ===== landing 子表 =====

    /** 1=自部署 dante; 2=第三方现成 socks5. */
    @NotNull(message = "provisionMode 不能为空")
    @Min(value = 1) @Max(value = 2)
    private Integer provisionMode;

    @NotNull(message = "socks5Port 不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer socks5Port;

    @NotBlank(message = "socks5Username 不能为空")
    @Size(max = 64)
    private String socks5Username;

    @NotBlank(message = "socks5Password 不能为空")
    @Size(max = 128)
    private String socks5Password;

    /** dante 日志关键字组合. */
    @NotBlank(message = "logLevel 不能为空")
    @Size(max = 64)
    private String logLevel;

    /** dante logoutput 路径. */
    @NotBlank(message = "logPath 不能为空")
    @Size(max = 255)
    private String logPath;

    @NotNull(message = "autostartEnabled 不能为空")
    @Min(value = 0) @Max(value = 1)
    private Integer autostartEnabled;

    @NotNull(message = "firewallEnabled 不能为空")
    @Min(value = 0) @Max(value = 1)
    private Integer firewallEnabled;

    @NotNull(message = "logRotateEnabled 不能为空")
    @Min(value = 0) @Max(value = 1)
    private Integer logRotateEnabled;

    @NotBlank(message = "installDir 不能为空")
    @Size(max = 255)
    private String installDir;

    @NotBlank(message = "confPath 不能为空")
    @Size(max = 255)
    private String confPath;

    @NotBlank(message = "pamFile 不能为空")
    @Size(max = 255)
    private String pamFile;

    @NotBlank(message = "pwdFile 不能为空")
    @Size(max = 255)
    private String pwdFile;

    @NotBlank(message = "systemdUnit 不能为空")
    @Size(max = 64)
    private String systemdUnit;

    // ===== SSH 凭据子表 =====
    // SSH 主机 = ipAddress (canonical); 不在凭据里

    @NotNull(message = "sshPort 不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer sshPort;

    @NotBlank(message = "sshUser 不能为空")
    @Size(max = 64)
    private String sshUser;

    @NotBlank(message = "sshPassword 不能为空")
    @Size(max = 255)
    private String sshPassword;

    /** SSH 握手超时 (秒); 留空走 SshSessions 默认值. */
    @Min(value = 5) @Max(value = 300)
    private Integer sshTimeoutSeconds;

    /** SSH 单条命令超时 (秒); 留空走默认. */
    @Min(value = 5) @Max(value = 300)
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传超时 (秒); 留空走默认. */
    @Min(value = 5) @Max(value = 600)
    private Integer sshUploadTimeoutSeconds;

    /** 装机整体超时 (秒); 留空走默认. */
    @Min(value = 60) @Max(value = 3600)
    private Integer installTimeoutSeconds;

    // ===== 账面子表 (可空; 任一非空即写入 billing) =====

    private BigDecimal costMonthlyUsd;

    @Min(value = 1, message = "账单日 1-28")
    @Max(value = 28, message = "账单日 1-28")
    private Integer billingCycleDay;

    private LocalDate expiresAt;
}
