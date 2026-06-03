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
     * 覆盖写入网卡周期累计字节 (agent 报绝对值; 内部同步刷 used_traffic_bytes = 入站 + 出站).
     *
     * @param serverId     server 主键
     * @param rxBytes      当周期入站累计
     * @param txBytes      当周期出站累计
     * @param bizUsedBytes socks5 业务流量累计 (落地机; null=老 agent 未上报, 不更新该列)
     */
    void applyNicTraffic(String serverId, long rxBytes, long txBytes, Long bizUsedBytes);

    /**
     * 批量查容量.
     *
     * @param serverIds server 主键集合
     * @return key=serverId, value=容量 DTO; 缺失 server 不在 map 里
     */
    Map<String, ResourceServerCapacityRespDTO> listByServerIds(Collection<String> serverIds);
}
