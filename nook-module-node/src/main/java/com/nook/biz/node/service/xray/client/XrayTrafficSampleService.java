package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;

/**
 * Xray 用户流量采样 Service 接口
 *
 * @author nook
 */
public interface XrayTrafficSampleService {

    /**
     * 采样指定 server 上所有 client 的流量增量入库
     *
     * @param serverId resource_server.id
     */
    void sampleServerTraffic(String serverId);

    /**
     * 获得单个 client 的总流量 (DB 累计 + xray 当前增量)
     *
     * @param clientId xray_client.id
     * @return 合并后的流量快照
     */
    XrayUserTrafficSnapshot getTotalTraffic(String clientId);
}
