package com.nook.biz.node.framework.ssh;

/**
 * SSH 本地端口转发句柄; 生命周期由 SshSession 管理 (随 session 一起关).
 * 同一对 (remoteHost, remotePort) 反复 openLocalForward 会返回缓存的同一实例.
 */
public interface PortForward {

    /** OS 分配的本地端口 (建端口转发时本地绑 0 的真实结果). */
    int localPort();
}
