package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;

/**
 * Xray 客户端流量采样 Service: 仅定时采样任务触发, 不对外暴露查询/重置.
 *
 * <p>对外的流量查询 / 重置在 {@link XrayClientTrafficService}, 由 controller 直调.
 *
 * @author nook
 */
public interface XrayClientTrafficSampleService {

    /**
     * 采样指定 server 上所有 client 的流量增量入库; 内部按 serverId 自查 xray_node.
     *
     * @param serverId resource_server.id
     * @return 采样统计
     */
    SampleStat sampleServerTraffic(String serverId);

    /**
     * 采样重载: 节点 DO 由调用方传入, 避免外层 (定时 sweep) 已拉表又重新单查.
     *
     * @param node xray_node DO; 不可为 null
     * @return 采样统计
     */
    SampleStat sampleServerTraffic(XrayNodeDO node);

    /**
     * 单次采样统计; 给 Job 聚合多 server 汇总日志用.
     *
     * @param upserted 实际落库的 client 数 (INSERT + UPDATE 累加)
     * @param skipped  远端有 DB 无的孤儿 counter 数
     */
    record SampleStat(int upserted, int skipped) {
        public static final SampleStat EMPTY = new SampleStat(0, 0);
    }
}
