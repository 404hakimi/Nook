package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.dal.dataobject.node.XrayServerDO;

/**
 * Xray 客户端流量采样 Service 接口
 *
 * @author nook
 */
public interface XrayClientTrafficSampleService {

    /**
     * 采样 server 上所有客户端的流量增量
     *
     * @param serverId 服务器编号
     * @return 采样统计
     */
    SampleStat sampleServerTraffic(String serverId);

    /**
     * 采样 server 上所有客户端的流量增量
     *
     * @param server xray 实例 DO
     * @return 采样统计
     */
    SampleStat sampleServerTraffic(XrayServerDO server);

    /**
     * 接收 agent 推送的客户端累计计数器
     *
     * @param serverId 服务器编号
     * @param stats    email → 当前累计
     * @return 采样统计
     */
    SampleStat applyAgentStats(String serverId, java.util.Map<String, AgentStatSnapshot> stats);

    /** agent 上报的单条客户端计数器快照 */
    record AgentStatSnapshot(long upBytes, long downBytes) {}

    /**
     * 单次采样统计
     *
     * @param upserted 实际落库的客户端数
     * @param skipped  远端有 DB 无的孤儿计数器数
     */
    record SampleStat(int upserted, int skipped) {
        public static final SampleStat EMPTY = new SampleStat(0, 0);
    }
}
