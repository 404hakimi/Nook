package com.nook.biz.node.framework.server.session;

import com.nook.biz.node.framework.ssh.SshChannel;
import com.nook.biz.node.framework.xray.inbound.grpc.InboundGrpcClient;
import com.nook.biz.node.framework.xray.stats.XrayStatsClient;

import java.time.Instant;

/** 一台 resource_server 的远程会话句柄; 共享一个 SSHClient + gRPC channel, 三类业务能力分别由 facet 暴露. */
public interface ServerSession extends AutoCloseable {

    /** 会话生命周期状态. */
    enum State {
        /** 正在握手 / 建端口转发, 不可对外服务. */
        CONNECTING,
        /** 已就绪, 可正常分发业务调用. */
        READY,
        /** 已失效, 终态; 必须由 manager 重新构造. */
        DEAD
    }

    /** 瞬时观测快照, 给运维查询接口与结构化日志用. */
    record Snapshot(
            String serverId,
            State state,
            Instant connectedAt,
            Instant lastUsedAt,
            Instant lastPingAt,
            int pingFailCount,
            String lastError
    ) {
    }

    /** 绑定的 resource_server.id. */
    String serverId();

    /** 当前状态; 状态非 READY 的会话不会被 acquire 返回. */
    State state();

    /** SSH 协议层心跳探活; 与 Xray 进程是否存活无关. */
    boolean ping();

    /** SSH 命令通道 facet (exec / execStream / uploadString). */
    SshChannel ssh();

    /** Xray gRPC stats facet (verifyConnectivity + readStat). */
    XrayStatsClient stats();

    /** Xray 入站用户管理 facet (addUser / removeUser). */
    InboundGrpcClient inbound();

    /** no-op: 引用归还由 manager 自管, 仅为 try-with-resources 语法糖. */
    @Override
    default void close() {
    }
}
