package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;

/**
 * Xray 客户端流量采样 Service: 仅定时 sample job 触发, 不对外暴露查询/重置.
 *
 * <p>对外的流量查询 / 重置在 {@link XrayClientTrafficService}, 由 controller 直调.
 *
 * @author nook
 */
public interface XrayClientTrafficSampleService {

    /**
     * 采样指定 server 上所有 client 的流量增量入库; 内部按 serverId 自查 xray_node
     *
     * @param serverId resource_server.id
     * @return 采样统计
     */
    SampleStat sampleServerTraffic(String serverId);

    /**
     * 采样重载: 节点 DO 由调用方传入, 避免外层 (定时 sweep) 已拉表又重新单查
     *
     * @param node xray_node DO; 不可为 null
     * @return 采样统计
     */
    SampleStat sampleServerTraffic(XrayNodeDO node);
}
