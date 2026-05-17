package com.nook.biz.node.controller.xray.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 运营手动 provision 入参; (memberUserId, ipId) 防重 + 字段间约束由 XrayClientValidator 校验.
 *
 * <p>共享 inbound 模型下, 协议 / 传输 / listen IP / flow 都是 server 级 inbound 的固有属性,
 * 不由 caller 传入; 这里只承载真正 per-client 的输入: server + 落地 IP + 会员 + 配额.
 *
 * @author nook
 */
@Data
public class XrayClientProvisionReqVO {

    @NotBlank(message = "serverId 必填")
    @Size(max = 36)
    private String serverId;

    @NotBlank(message = "ipId 必填")
    @Size(max = 36)
    private String ipId;

    @NotBlank(message = "memberUserId 必填")
    @Size(max = 36)
    private String memberUserId;

    /** 流量上限(字节); 0 = 不限. */
    @Min(value = 0, message = "totalBytes 不能为负")
    private Long totalBytes;

    /** 到期时间戳(毫秒); 0 = 永久. */
    @Min(value = 0, message = "expiryEpochMillis 不能为负")
    private Long expiryEpochMillis;

    /** 单客户端最多并发源 IP 数; 0 = 不限. */
    @Min(value = 0, message = "limitIp 不能为负")
    private Integer limitIp;
}
