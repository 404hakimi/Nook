package com.nook.biz.xray.backend.dto;

/**
 * 列 inbound 时返回的精简视图。
 * 后台管理界面用，让运营把"3x-ui 面板上的 inbound" 与 "IP 池里的 IP" 关联起来。
 *   externalInboundRef  threexui=面板 inboundId; xray-grpc=inbound tag
 *   remark              备注/名称
 *   protocol            vless/vmess/trojan/shadowsocks
 *   port                监听端口
 *   enabled             该 inbound 是否启用
 *   clientCount         当前已挂载的 client 数(给运营粗略判断容量)
 */
public record XrayInboundInfo(
        String externalInboundRef,
        String remark,
        String protocol,
        int port,
        boolean enabled,
        int clientCount
) {
}
