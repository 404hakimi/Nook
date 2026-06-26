package com.nook.biz.node.framework.xray.install;

import lombok.Builder;
import lombok.Data;

/**
 * Xray 装机请求; 后台配置落库后下发给 agent 的结构化配置, agent 据此用内置逻辑本地装机.
 *
 * <p>后台不再拼 bash / 不再远端执行脚本; inbound JSON 仍由后台协议策略渲染, agent 视为不透明直接写盘.
 *
 * @author nook
 */
@Data
@Builder
public class XrayDeployRequest {

    /** 线路机 ID. */
    private String serverId;

    /** 目标 xray 版本 (latest 或具体版本; agent 据此判幂等). */
    private String xrayVersion;

    /** 强制重装 (即使版本一致也重新下载). */
    private boolean forceReinstall;

    /** 装 / 启用 UFW 防火墙. */
    private boolean installUfw;

    /** 设系统时区 Asia/Shanghai. */
    private boolean setTimezone;

    /** 启用 logrotate 日志轮转. */
    private boolean logRotate;

    /** 共享 inbound 端口 (agent 放行 UFW + 起服). */
    private Integer sharedInboundPort;

    /** 已渲染的 xray inbound JSON (agent 不透明写入 config.json 的 inbounds). */
    private String inboundConfigJson;

    /** 对外域名 FQDN (vmess-tls; 空 = 无 TLS, agent 跳过写证书). */
    private String domain;

    /** 后台签好的全链证书 PEM (agent 直接写盘; 仅绑域名时有值). agent 不再直连 Cloudflare/acme. */
    private String tlsCertPem;

    /** 后台签好的证书私钥 PEM (与 tlsCertPem 配对; 仅绑域名时有值). */
    private String tlsKeyPem;

    /** agent 本地装机超时秒数 (下载 xray binary 为主; 证书由后台签好下发, agent 不再跑 acme). */
    private int timeoutSeconds;
}
