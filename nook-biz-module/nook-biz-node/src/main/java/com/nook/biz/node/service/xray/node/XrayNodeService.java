package com.nook.biz.node.service.xray.node;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;

import java.time.LocalDateTime;

/**
 * Xray 节点 (server 上跑的 xray 实例) 业务服务.
 *
 * @author nook
 */
public interface XrayNodeService {

    /**
     * 部署成功后初始化 / 更新 xray 节点配置 + slot 池, 幂等; 同事务保证 "xray_node 行存在 ↔ slot 池已初始化".
     *
     * @param serverId       关联 resource_server.id
     * @param xrayVersion    安装的 xray 版本
     * @param xrayApiPort    xray 内置 api server 端口 (loopback)
     * @param xrayLogDir     日志目录
     * @param slotPoolSize   slot 池大小
     * @param slotPortBase   slot 端口段起点
     */
    void upsert(String serverId,
                String xrayVersion,
                int xrayApiPort,
                String xrayLogDir,
                int slotPoolSize,
                int slotPortBase);

    /**
     * 加载节点配置; 不存在抛 SERVER_STATE_NOT_FOUND.
     *
     * @param serverId 关联 resource_server.id
     * @return XrayNodeDO
     */
    XrayNodeDO loadOrThrow(String serverId);

    /**
     * 加载节点配置; 不存在返回 null (用于判断 server 是否已装 xray).
     *
     * @param serverId resource_server.id
     * @return XrayNodeDO 或 null
     */
    XrayNodeDO loadOrNull(String serverId);

    /**
     * 标记 replay 已完成, 更新 last_xray_uptime + updated_at.
     *
     * @param serverId   server
     * @param xrayUptime 当前探测到的 xray 启动时间
     */
    void markReplayDone(String serverId, LocalDateTime xrayUptime);
}
