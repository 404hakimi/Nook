package com.nook.biz.node.framework.ssh;

import java.time.Instant;

/**
 * 一台 server 的纯 SSH 会话句柄, 只关心 SSH 协议本身, 不知道 gRPC / xray / 任何业务.
 *
 * @author nook
 */
public interface SshSession extends AutoCloseable {

    /**
     * 给运维查询 / 日志用的瞬时快照.
     *
     * @author nook
     */
    record Snapshot(
            /** resource_server.id */
            String serverId,
            /** 会话首次建立时刻 */
            Instant connectedAt
    ) {
    }

    /**
     * 绑定的 resource_server.id.
     *
     * @return serverId
     */
    String serverId();

    /**
     * 是否仍然存活 (TCP / SSH 协议层未断).
     *
     * @return true 表示活
     */
    boolean isAlive();

    /**
     * SSH 命令通道 facet (exec / execStream / uploadString).
     *
     * @return SshChannel
     */
    SshChannel ssh();

    /**
     * 开本地端口转发到远端 host:port; 同 (remoteHost, remotePort) 复用缓存的 forward.
     *
     * @param remoteHost 远端目标 host
     * @param remotePort 远端目标 port
     * @return PortForward
     */
    PortForward openLocalForward(String remoteHost, int remotePort);

    /** no-op: 引用归还由 manager 自管, 仅为 try-with-resources 语法糖. */
    @Override
    default void close() {
    }
}
