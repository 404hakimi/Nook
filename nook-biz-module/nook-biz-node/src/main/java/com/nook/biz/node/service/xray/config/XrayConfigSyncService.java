package com.nook.biz.node.service.xray.config;

/** Xray 配置同步: DB client 状态 → 远端 xray.json + reload; 编排 framework 的 inbound/outbound/routing 三个 reconciler. */
public interface XrayConfigSyncService {

    /** 将指定 server 的 xray.json 与 DB 状态对齐并重启 xray; 失败抛 BusinessException, 调用方决定回滚. */
    void sync(String serverId);
}
