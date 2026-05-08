package com.nook.biz.xray.controller.client.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动给某 (server, ip, member) 三元组开通一条 client。
 * 业务流程触发(订阅成功后)走 XrayClientApi.provision；这里是后台管理员手动 provision 的入口。
 */
@Data
public class XrayClientProvisionReqVO {

    @NotBlank(message = "serverId 不能为空")
    private String serverId;

    @NotBlank(message = "ipId 不能为空")
    private String ipId;

    @NotBlank(message = "memberUserId 不能为空")
    private String memberUserId;

    /** 远端 inbound 引用键(threexui=面板 inboundId, xray-grpc=inbound tag) */
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
