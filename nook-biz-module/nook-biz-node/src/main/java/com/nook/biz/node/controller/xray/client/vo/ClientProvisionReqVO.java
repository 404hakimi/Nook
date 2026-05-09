package com.nook.biz.node.controller.xray.client.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 运营手动 provision 入参; 业务自动 provision 走 XrayClientApi. */
@Data
public class ClientProvisionReqVO {

    @NotBlank(message = "serverId 不能为空")
    private String serverId;

    @NotBlank(message = "ipId 不能为空")
    private String ipId;

    @NotBlank(message = "memberUserId 不能为空")
    private String memberUserId;

    /** 远端 Xray inbound tag (xray.json 里 inbound[].tag 字段). */
    @NotBlank(message = "externalInboundRef 不能为空")
    @Size(max = 128)
    private String externalInboundRef;

    @NotBlank(message = "protocol 不能为空")
    @Pattern(regexp = "vless|vmess|trojan|shadowsocks", message = "protocol 必须是 vless/vmess/trojan/shadowsocks")
    private String protocol;

    /** 传输方式；可选 */
    @Size(max = 32)
    private String transport;

    @Size(max = 45)
    private String listenIp;

    /** 客户端实际连接端口；通常 = inbound 的 port */
    private Integer listenPort;

    /** 流量上限(字节); 0 = 不限 */
    private Long totalBytes;

    /** 到期时间戳(毫秒); 0 = 永久 */
    private Long expiryEpochMillis;

    private Integer limitIp;

    /** vless flow，例 xtls-rprx-vision；其它协议留空 */
    @Size(max = 64)
    private String flow;
}
