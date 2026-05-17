package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Xray 节点上的活跃客户端 (= 落地数占用) 视图项.
 *
 * <p>每行 = 一个 xray_client 行, 关联 ip_pool 拉 ip_address 给运维显示."落地"维度对账 / 容量展示用.
 *
 * @author nook
 */
@Data
public class XrayTouchdownItemRespVO {

    /** xray_client.id (UUID 32 hex), 也是远端 outbound tag. */
    private String clientId;

    /** 关联 resource_ip_pool.id; 落地 IP 池里的引用. */
    private String ipId;

    /** 关联 IP 地址 (ip_pool.ip_address join 出来给前端显示, 不入对账). */
    private String ipAddress;

    /** 用户 email; 也是 inbound user 标识 + routing rule user 匹配键. */
    private String clientEmail;

    /** 协议 (vmess/vless/trojan); 当前固定 vmess. */
    private String protocol;

    /** 传输 (tcp/ws/grpc); 当前固定 ws. */
    private String transport;

    /** 状态: 1=运行 2=已停 3=待同步 4=远端缺失. */
    private Integer clientStatus;

    /** 创建时间 (provision 落库时刻). */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
