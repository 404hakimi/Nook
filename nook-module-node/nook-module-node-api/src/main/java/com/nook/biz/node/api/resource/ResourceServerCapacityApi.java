package com.nook.biz.node.api.resource;

/** Server 容量 (NIC 流量累计字节数) 上报契约. */
public interface ResourceServerCapacityApi {

    /**
     * 累加 NIC 流量字节数到 resource_server_capacity.used_traffic_bytes.
     *
     * @param serverId server 主键
     * @param bytes    本周期新增字节 (rxBytes + txBytes)
     */
    void addUsedTrafficBytes(String serverId, long bytes);
}
