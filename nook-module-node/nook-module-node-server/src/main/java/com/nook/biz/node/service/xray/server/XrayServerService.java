package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.dal.dataobject.node.XrayServerDO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * Xray 实例元数据 Service 接口
 *
 * @author nook
 */
public interface XrayServerService {

    /**
     * 幂等写入实例元数据
     *
     * @param entity 实例元数据
     */
    void upsert(XrayServerDO entity);

    /**
     * 按 serverId 取实例元数据
     *
     * @param serverId 服务器编号
     * @return 实例元数据
     */
    XrayServerDO get(String serverId);

    /**
     * 按 serverId 批量取实例元数据
     *
     * @param serverIds 服务器编号集合
     * @return serverId → 实例元数据
     */
    Map<String, XrayServerDO> listByServerIds(Collection<String> serverIds);

    /**
     * 标记 replay 完成
     *
     * @param serverId   服务器编号
     * @param xrayUptime 探测到的 xray 启动时间
     */
    void markReplayDone(String serverId, LocalDateTime xrayUptime);
}
