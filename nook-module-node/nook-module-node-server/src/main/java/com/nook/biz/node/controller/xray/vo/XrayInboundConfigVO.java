package com.nook.biz.node.controller.xray.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Xray 共享 inbound 配置; 协议形态 + 监听 + 协议特定参数 (ws / reality / 域名), 协议特定字段的必填性由对应 InboundProtocol 校验
 *
 * @author nook
 */
@Data
public class XrayInboundConfigVO {

    /** 协议; vmess (走 ws) 或 vless (走 reality). */
    @NotBlank(message = "protocol 必填")
    @Pattern(regexp = "vmess|vless|trojan", message = "protocol 必须是 vmess / vless / trojan 之一")
    @Size(max = 16)
    private String protocol;

    /** 传输; vmess=ws, vless-reality=tcp; 随协议联动. */
    @NotBlank(message = "transport 必填")
    @Pattern(regexp = "tcp|ws|grpc|h2|quic", message = "transport 必须是 tcp / ws / grpc / h2 / quic 之一")
    @Size(max = 32)
    private String transport;

    /** 监听 IP; 当前固定 0.0.0.0. */
    @NotBlank(message = "listenIp 必填")
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^::$|^[0-9a-fA-F:]+$",
            message = "listenIp 必须是合法 IPv4 / IPv6")
    @Size(max = 45)
    private String listenIp;

    /** 监听端口; 默认 443. */
    @NotNull(message = "sharedInboundPort 必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer sharedInboundPort;

    /** WebSocket transport path; vmess 必填 (由 VmessWsProtocol 校验), vless 不用. */
    @Pattern(regexp = "^$|^/[A-Za-z0-9_\\-/]{0,127}$", message = "wsPath 必须以 / 开头, 仅字母数字_-/")
    @Size(max = 128)
    private String wsPath;

    /** REALITY 偷取目标主机名 (如 www.bing.com; 预设或自定义); vless 必填 (由 VlessRealityProtocol 校验格式). */
    @Size(max = 253)
    private String realityDest;

    /**
     * 绑定的根域 system_domain.id; vmess 选了走 TLS (CF A 记录 + acme + xray TLS), 空走纯 ws; vless 不用. 装机时据此取根域 + CF Token.
     */
    @Size(max = 32)
    private String domainId;

    /** 二级域名标签 (如 frontline-jp-1); vmess 绑域名 (domainId 非空) 时必填. 完整 FQDN = subdomain + "." + 根域. */
    @Pattern(regexp = "^$|^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$",
            message = "subdomain 只能含字母数字与连字符 (可多级, 点分隔)")
    @Size(max = 128)
    private String subdomain;
}
