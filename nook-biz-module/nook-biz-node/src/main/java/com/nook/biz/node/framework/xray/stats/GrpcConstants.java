package com.nook.biz.node.framework.xray.stats;

/** Xray gRPC 协议层硬编码: stat 名 + 错误描述关键词. */
public final class GrpcConstants {

    private GrpcConstants() {
    }

    /** API inbound 上行流量 stat 名; verifyConnectivity 探活用 (一旦 statsInboundUplink=true 必存在). */
    public static final String STAT_API_INBOUND_UPLINK = "inbound>>>api>>>traffic>>>uplink";

    // Xray HandlerService 用 errors.New(...) 抛错 → gRPC code=UNKNOWN, 仅靠 description 字符串区分;
    // 这是 Xray 设计限制, 上游 message 改了只需在此处更新.

    /** AlterInbound AddUser 时, email 已存在的 description 关键词. */
    public static final String DESC_USER_DUPLICATE = "already exists";

    /** AlterInbound RemoveUser 时, email 不存在的 description 关键词. */
    public static final String DESC_USER_NOT_FOUND = "not found";
}
