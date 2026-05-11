package com.nook.framework.ssh.core;

/**
 * SSH 会话作用域; 同一 server 按 scope 隔离 cache, 防长任务跟短任务互相 invalidate.
 *
 * <p>新增 scope 时在此 enum 内统一管理, 业务侧不允许另造.
 *
 * @author nook
 */
public enum SshSessionScope {

    /** 默认短任务: provision / revoke / rotate / status / setAutostart 等. */
    SHARED,

    /** 长任务独占: deploy / install / swap / bbr 等流式部署, 跑 1-10 min. */
    INSTALL,

    /** 调度/对账任务独占: reconciler / syncOne / replayServer / replayIfRestarted. */
    RECONCILE
}
