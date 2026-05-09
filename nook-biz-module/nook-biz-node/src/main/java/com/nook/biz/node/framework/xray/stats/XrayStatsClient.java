package com.nook.biz.node.framework.xray.stats;

/** Xray gRPC StatsService 调用; 探活 + 用户/inbound 流量读取. */
public interface XrayStatsClient {

    /** 探活: 走 stats:api inbound uplink (一旦 statsInboundUplink=true 必存在); 失败抛 BACKEND_UNREACHABLE. */
    void verifyConnectivity();

    /** 读单条 stat; reset=true 原子返回旧值并清零; 不存在的 stat 当 0. */
    long readStat(String statName, boolean reset);
}
