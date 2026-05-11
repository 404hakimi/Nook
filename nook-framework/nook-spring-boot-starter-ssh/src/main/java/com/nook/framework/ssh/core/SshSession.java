package com.nook.framework.ssh.core;

import java.time.Instant;

/**
 * 一台 server 的纯 SSH 会话句柄, 只关心 SSH 协议本身, 不感知业务.
 *
 * @author nook
 */
public interface SshSession extends AutoCloseable {

    /**
     * 给运维查询 / 日志用的瞬时快照.
     *
     * @param serverId    会话绑定的 serverId
     * @param connectedAt 会话首次建立时刻
     */
    record Snapshot(String serverId, Instant connectedAt) {
    }

    /**
     * 绑定的 serverId (= SessionCredential.serverId).
     *
     * @return serverId
     */
    String serverId();

    /**
     * 当前会话绑定的凭据快照, 含 sshOp / sshUpload / install 各档超时.
     *
     * @return SessionCredential
     */
    SessionCredential cred();

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
     * no-op: 引用归还由 manager 自管, 仅为 try-with-resources 语法糖.
     */
    @Override
    default void close() {
    }
}
