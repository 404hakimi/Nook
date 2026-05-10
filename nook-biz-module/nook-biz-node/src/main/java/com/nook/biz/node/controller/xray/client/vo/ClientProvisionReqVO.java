package com.nook.biz.node.controller.xray.client.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 运营手动 provision 入参; 业务自动 provision 走 XrayClientApi;
 * (memberUserId, ipId) 防重由 XrayClientValidator 校验.
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

    /** 流量上限(字节); 0 = 不限. */
    @Min(value = 0, message = "totalBytes 不能为负")
    private Long totalBytes;

    /** 到期时间戳(毫秒); 0 = 永久. */
    @Min(value = 0, message = "expiryEpochMillis 不能为负")
    private Long expiryEpochMillis;

    @Min(value = 0, message = "limitIp 不能为负")
    private Integer limitIp;

    /** vless flow, 例 xtls-rprx-vision; 其它协议留空. */
    @Size(max = 64)
    private String flow;

    // 1:1 + slot 模型下 nook 自动决定, 前端可不传; 传了也会被业务侧覆写
    @Deprecated
    private String externalInboundRef;

    @Deprecated
    private String transport;

    @Deprecated
    private String listenIp;

    @Deprecated
    private Integer listenPort;
}
