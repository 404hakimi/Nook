package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;

/**
 * Xray 用户流量采样 Service 接口
 *
 * @author nook
 */
public interface XrayTrafficSampleService {

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

    /**
     * 获得单个 client 的总流量 (DB 累计 + xray 当前增量)
     *
     * @param clientId xray_client.id
     * @return 合并后的流量快照
     */
    XrayUserTrafficSnapshot getTotalTraffic(String clientId);
}
