package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerQuotaRespDTO;

import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器额度 Api 接口
 *
 * @author nook
 */
public interface ResourceServerQuotaApi {

    /**
     * 写入 agent 上报的网卡累计流量 + 用户业务上下行累计
     *
     * <p>上报为绝对累计值, 内部做差累加进当周期测量行.
     *
     * @param serverId     服务器ID
     * @param rxBytes      网卡入站累计字节
     * @param txBytes      网卡出站累计字节
     * @param bizUpBytes   socks5 用户上行累计字节; null 表示本次不更新
     * @param bizDownBytes socks5 用户下行累计字节; null 表示本次不更新
     */
    void applyNicTraffic(String serverId, long rxBytes, long txBytes, Long bizUpBytes, Long bizDownBytes);

    /**
     * 批量查服务器额度 + 当周期测量
     *
     * @param serverIds 服务器ID集合
     * @return 服务器ID → 额度测量 DTO (缺失的不在 map 内)
     */
    Map<String, ResourceServerQuotaRespDTO> listByServerIds(Collection<String> serverIds);
}
