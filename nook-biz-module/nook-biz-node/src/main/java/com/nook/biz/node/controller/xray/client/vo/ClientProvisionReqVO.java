package com.nook.biz.node.controller.xray.client.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 运营手动 provision 入参; (memberUserId, ipId) 防重 + 字段间约束由 XrayClientValidator 校验.
 *
 * @author nook
 */
@Data
public class ClientProvisionReqVO {

    @NotBlank(message = "serverId 必填")
    @Size(max = 36)
    private String serverId;

    @NotBlank(message = "ipId 必填")
    @Size(max = 36)
    private String ipId;

    @NotBlank(message = "memberUserId 必填")
    @Size(max = 36)
    private String memberUserId;

    /** 协议 vless / vmess / trojan. */
    @NotBlank(message = "protocol 必填")
    @Pattern(regexp = "vless|vmess|trojan", message = "protocol 必须是 vless / vmess / trojan 之一")
    @Size(max = 16)
    private String protocol;

    /** 传输层: tcp / ws / grpc / h2 / quic; 当前 inbound 仅 tcp 走通, 其它已收到入参但 streamSettings 暂未生效. */
    @NotBlank(message = "transport 必填")
    @Pattern(regexp = "tcp|ws|grpc|h2|quic", message = "transport 必须是 tcp / ws / grpc / h2 / quic 之一")
    @Size(max = 32)
    private String transport;

    /** 监听 IP, 0.0.0.0 = 所有 IPv4 接口, :: = 所有 IPv6 接口. */
    @NotBlank(message = "listenIp 必填")
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^::$|^::[0-9a-fA-F]+$|^[0-9a-fA-F:]+$",
            message = "listenIp 必须是合法 IPv4 / IPv6")
    @Size(max = 45)
    private String listenIp;

    /** 流量上限(字节); 0 = 不限. */
    @Min(value = 0, message = "totalBytes 不能为负")
    private Long totalBytes;

    /** 到期时间戳(毫秒); 0 = 永久. */
    @Min(value = 0, message = "expiryEpochMillis 不能为负")
    private Long expiryEpochMillis;

    /** 单客户端最多并发源 IP 数; 0 = 不限. */
    @Min(value = 0, message = "limitIp 不能为负")
    private Integer limitIp;

    /** vless flow, 例 xtls-rprx-vision; vmess / trojan 必须为空 (Validator 跨字段校验). */
    @Size(max = 64)
    private String flow;
}
