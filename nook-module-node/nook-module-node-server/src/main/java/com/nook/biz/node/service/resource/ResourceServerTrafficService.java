package com.nook.biz.node.service.resource;

import com.nook.biz.node.entity.ResourceServerTrafficDO;

/**
 * 服务器流量计量 Service 接口
 *
 * @author nook
 */
public interface ResourceServerTrafficService {

    /**
     * 处理 agent 上报的网卡累计流量 + 用户业务上下行累计
     *
     * @param serverId     服务器编号
     * @param cumRxBytes   网卡累计入站字节(vnstat total, 跨月不清零)
     * @param cumTxBytes   网卡累计出站字节
     * @param bizUpBytes   socks5 用户上行累计(落地机; null=未上报, 不更新)
     * @param bizDownBytes socks5 用户下行累计(落地机; null=未上报, 不更新)
     */
    void applyNicTraffic(String serverId, long cumRxBytes, long cumTxBytes, Long bizUpBytes, Long bizDownBytes);

    /**
     * 某服务器当周期测量行 (end_time 空); 无返 null
     *
     * @param serverId 服务器编号
     * @return 当周期测量行
     */
    ResourceServerTrafficDO getCurrent(String serverId);
}
