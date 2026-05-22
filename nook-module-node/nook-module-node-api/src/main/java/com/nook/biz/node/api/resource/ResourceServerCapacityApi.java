package com.nook.biz.node.api.resource;

/**
 * 资源服务器容量 Api 接口
 *
 * @author nook
 */
public interface ResourceServerCapacityApi {

    /**
     * 累加 NIC 流量字节数到 resource_server_capacity.used_traffic_bytes.
     *
     * @param serverId server 主键
     * @param bytes    本周期新增字节 (rxBytes + txBytes)
     */
    void addUsedTrafficBytes(String serverId, long bytes);
}
