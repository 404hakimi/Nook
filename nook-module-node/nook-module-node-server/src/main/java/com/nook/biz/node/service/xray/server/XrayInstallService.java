package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.api.enums.XrayInstallStatusEnum;
import com.nook.biz.node.entity.XrayInstallDO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * Xray 实例元数据 Service 接口
 *
 * @author nook
 */
public interface XrayInstallService {

    /**
     * 幂等写入实例元数据
     *
     * @param entity 实例元数据
     */
    void upsert(XrayInstallDO entity);

    /**
     * 按 serverId 取实例元数据
     *
     * @param serverId 服务器编号
     * @return 实例元数据
     */
    XrayInstallDO get(String serverId);

    /**
     * 同一根域下二级标签是否已被别的线路机占用 (重装时排除自身)
     *
     * @param domainId        根域 system_domain.id
     * @param subdomain       二级标签
     * @param excludeServerId 排除的服务器编号 (当前机)
     * @return true = 已被其他机占用
     */
    boolean isSubdomainTaken(String domainId, String subdomain, String excludeServerId);

    /**
     * 标记 replay 完成
     *
     * @param serverId   服务器编号
     * @param xrayUptime 探测到的 xray 启动时间
     */
    void markReplayDone(String serverId, LocalDateTime xrayUptime);

    /**
     * 更新装机状态 (agent 回报后); OK 时同步置 installedAt = now
     *
     * @param serverId 服务器编号
     * @param status   装机状态
     */
    void markInstallStatus(String serverId, XrayInstallStatusEnum status);
}
