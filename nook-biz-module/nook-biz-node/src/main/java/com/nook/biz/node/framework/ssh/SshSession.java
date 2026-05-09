package com.nook.biz.node.framework.ssh;

import java.time.Instant;

/** 一台 server 的纯 SSH 会话句柄; 只关心 SSH 协议本身, 不知道 gRPC / xray / 任何业务. */
public interface SshSession extends AutoCloseable {

    /** 给运维查询 / 日志用的瞬时快照. */
    record Snapshot(String serverId, Instant connectedAt) {
    }

    /** 绑定的 resource_server.id. */
    String serverId();

    /** 是否仍然存活 (TCP / SSH 协议层未断). */
    boolean isAlive();

    /** SSH 命令通道 facet (exec / execStream / uploadString). */
    SshChannel ssh();

    /** 开本地端口转发到远端 host:port; 转发的生命周期归调用方 (拿到 PortForward 自己 close). */
    PortForward openLocalForward(String remoteHost, int remotePort);

    /** no-op: 引用归还由 manager 自管, 仅为 try-with-resources 语法糖. */
    @Override
    default void close() {
    }
}
