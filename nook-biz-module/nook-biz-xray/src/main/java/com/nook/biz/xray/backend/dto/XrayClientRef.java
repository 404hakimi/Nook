package com.nook.biz.xray.backend.dto;

/**
 * 操作既有 client 的引用。
 * 把 ref/uuid/email 三件套塞一起避免参数列表难维护。
 *   externalInboundRef threexui=面板 inboundId 字符串, xray-grpc=inbound tag
 *   uuid               协议级密钥(vless/vmess UUID, trojan password); 3xui delClient 用它定位
 *   email              人类可读标识; 3xui getClientTraffics 用它，gRPC 用作 user.email
 */
public record XrayClientRef(
        String externalInboundRef,
        String uuid,
        String email
) {
}
