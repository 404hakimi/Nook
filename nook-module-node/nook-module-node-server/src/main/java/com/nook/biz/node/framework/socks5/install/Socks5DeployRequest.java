package com.nook.biz.node.framework.socks5.install;

import lombok.Builder;
import lombok.Data;

/**
 * SOCKS5 (dante) 装机请求; 后台从 socks5_install 期望态读出下发给 landing agent, agent 内置 Go 本地装 dante
 *
 * <p>取代退场的后台 bash 装机 (socks5-dante.sh.tmpl); 经 AES 控制通道下发 (/socks5/deploy), token 不过线.
 * 字段与 agent socks5deploy.Request 一一对应; 服务器重置后据 socks5_install 表全字段可重新下发重建.
 *
 * @author nook
 */
@Data
@Builder
public class Socks5DeployRequest {

    /** 落地机 ID. */
    private String serverId;

    /** SOCKS5 监听端口. */
    private Integer socks5Port;

    /** SOCKS5 用户名 (PAM 认证). */
    private String socks5Username;

    /** SOCKS5 密码 (明文; 经 AES 控制通道下发, 不过明文线). */
    private String socks5Password;

    /** dante 日志关键字组合 (空格分隔, 如 connect disconnect error). */
    private String logLevel;

    /** dante logoutput 日志路径. */
    private String logPath;

    /** 安装目录 (下挂 logs/etc). */
    private String installDir;

    /** danted.conf 绝对路径. */
    private String confPath;

    /** PAM 配置文件路径. */
    private String pamFile;

    /** htpasswd 密码文件路径. */
    private String pwdFile;

    /** systemd 服务名 (dante apt 包默认 danted; 空则 agent 兜底 danted). */
    private String systemdUnit;

    /** 部署时配 UFW (会放行 SSH + 控制口 44844 + SOCKS5; 不放控制口会自锁). */
    private boolean firewallEnabled;

    /** 配 logrotate 日志轮转. */
    private boolean logRotateEnabled;

    /** SSH 端口 (UFW 放行防把自己锁外). */
    private Integer sshPort;

    /** agent 本地装机超时秒数. */
    private int timeoutSeconds;
}
