package com.nook.biz.node.service.xray.config;

/**
 * Xray 配置同步: DB client 状态 → 远端 xray.json + reload, 编排 framework 三个 reconciler.
 *
 * @author nook
 */
public interface XrayConfigSyncService {

    /**
     * 将指定 server 的 xray.json 与 DB 状态对齐并重启 xray, 失败抛 BusinessException 由调用方决定回滚.
     *
     * @param serverId resource_server.id
     */
    void sync(String serverId);
}
