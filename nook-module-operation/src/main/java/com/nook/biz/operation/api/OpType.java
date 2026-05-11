package com.nook.biz.operation.api;

/**
 * 危险操作类型集中枚举; 各业务模块新增 op 时在此追加一项 + 写对应 OperationHandler 即可.
 *
 * <p>命名约定: 资源_动作; server 级用 SERVER_ / XRAY_ 前缀, client 级用 CLIENT_ 前缀.
 *
 * @author nook
 */
public enum OpType {

    // ===== xray daemon 级 =====
    /** 重启 xray (systemctl restart) */
    XRAY_RESTART,
    /** 部署 / 重装 xray (跑安装脚本) */
    SERVER_PROVISION,
    /** 开/关 xray 开机自启 */
    SERVER_AUTOSTART,

    // ===== client 生命周期 =====
    /** 开通 client (远端 ADI/ADO + DB 落) */
    CLIENT_PROVISION,
    /** 吊销 client (远端 RMI/RMO + DB 硬删) */
    CLIENT_REVOKE,
    /** 轮换 client 协议密钥 (RMI + ADI) */
    CLIENT_ROTATE,
    /** 单 client 重推 (远端缺/孤儿时手动修) */
    CLIENT_SYNC,

    // ===== server-wide 一致性 =====
    /** 全量 replay (DB → server, 跑该 server 所有 client) */
    SERVER_REPLAY,
    /** 对账 (探 xray uptime; 重启后 replay) — 定时器和手动入口都走这里 */
    SERVER_RECONCILE,
    ;
}
