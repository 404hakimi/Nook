package com.nook.biz.operation.api;

/**
 * 危险操作类型集中枚举; 各业务模块新增 op 时在此追加一项 + 写对应 OperationHandler
 *
 * <p>命名约定: 资源_动作; server 级用 SERVER_ / XRAY_ 前缀, client 级用 CLIENT_ 前缀.
 * 中文显示名落 op_config.name, admin 可在线改.
 *
 * @author nook
 */
public enum OpType {

    // ===== xray daemon 级 =====
    XRAY_RESTART,
    SERVER_PROVISION,
    SERVER_AUTOSTART,

    // ===== client 生命周期 =====
    CLIENT_PROVISION,
    CLIENT_REVOKE,
    CLIENT_ROTATE,
    CLIENT_SYNC,

    // ===== server-wide 一致性 =====
    SERVER_REPLAY,
    SERVER_RECONCILE,
    ;
}
