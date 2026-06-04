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
     * 覆盖写入网卡周期累计流量
     *
     * <p>agent 上报绝对值, 内部同步刷新已用流量 (入站 + 出站).
     *
     * @param serverId     服务器ID
     * @param rxBytes      当周期入站累计字节
     * @param txBytes      当周期出站累计字节
     * @param bizUsedBytes 落地机 socks5 业务流量累计字节; null 表示本次不更新
     */
    void applyNicTraffic(String serverId, long rxBytes, long txBytes, Long bizUsedBytes);

    /**
     * 批量查服务器容量
     *
     * @param serverIds 服务器ID集合
     * @return 服务器ID → 容量 DTO (缺失的不在 map 内)
     */
    Map<String, ResourceServerCapacityRespDTO> listByServerIds(Collection<String> serverIds);
}
