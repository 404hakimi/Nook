package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;

import java.util.Collection;
import java.util.Map;

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

    /**
     * 批量查容量.
     *
     * @param serverIds server 主键集合
     * @return key=serverId, value=容量 DTO; 缺失 server 不在 map 里
     */
    Map<String, ResourceServerCapacityRespDTO> listByServerIds(Collection<String> serverIds);
}
