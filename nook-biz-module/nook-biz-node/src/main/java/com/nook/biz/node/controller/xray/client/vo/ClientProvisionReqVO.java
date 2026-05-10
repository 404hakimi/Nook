package com.nook.biz.node.controller.xray.client.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 运营手动 provision 入参; 业务自动 provision 走 XrayClientApi.
 *
 * <p>1:1 + slot 模型下的字段语义:
 * <ul>
 *   <li>必填: serverId / ipId / memberUserId / protocol</li>
 *   <li>可选: totalBytes / expiryEpochMillis / limitIp / flow (业务配额参数)</li>
 *   <li>已自动化, 前端可不传 (业务侧覆写): externalInboundRef / transport / listenIp / listenPort —
 *       nook 自动按 slot 分配 (in_slot_NN tag, port = slot_port_base + slot_index, listen 0.0.0.0, transport tcp)</li>
 * </ul>
 *
 * @author nook
 */
@Data
public class ClientProvisionReqVO {

    @NotBlank(message = "serverId 不能为空")
    private String serverId;

    @NotBlank(message = "ipId 不能为空")
    private String ipId;

    @NotBlank(message = "memberUserId 不能为空")
    private String memberUserId;

    @NotBlank(message = "protocol 不能为空")
    @Pattern(regexp = "vless|vmess|trojan", message = "protocol 必须是 vless/vmess/trojan")
    private String protocol;

    /** 流量上限(字节); 0 = 不限 */
    private Long totalBytes;

    /** 到期时间戳(毫秒); 0 = 永久 */
    private Long expiryEpochMillis;

    private Integer limitIp;

    /** vless flow, 例 xtls-rprx-vision; 其它协议留空 */
    @Size(max = 64)
    private String flow;

    // ===== 兼容字段 (1:1 模型下 nook 自动决定, 前端可不传; 传了也会被业务侧覆写) =====

    /** 已废弃: nook 自动生成 in_slot_NN, 前端可不传; 传了被忽略. */
    @Size(max = 128)
    @Deprecated
    private String externalInboundRef;

    /** 已废弃: nook 自动决定 transport=tcp; 前端可不传. */
    @Size(max = 32)
    @Deprecated
    private String transport;

    /** 已废弃: nook 自动 0.0.0.0; 前端可不传. */
    @Size(max = 45)
    @Deprecated
    private String listenIp;

    /** 已废弃: nook 自动按 slot 分配端口; 前端可不传. */
    @Deprecated
    private Integer listenPort;
}
