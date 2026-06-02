package com.nook.biz.node.service.resource;

/**
 * 服务器流量计量 Service 接口
 *
 * @author nook
 */
public interface ResourceServerTrafficService {

    /**
     * 处理 agent 上报的网卡累计流量
     *
     * @param serverId     服务器编号
     * @param cumRxBytes   网卡累计入站字节(vnstat total, 跨月不清零)
     * @param cumTxBytes   网卡累计出站字节
     * @param bizUsedBytes socks5 业务流量累计(落地机; null=未上报, 不更新)
     */
    void applyNicTraffic(String serverId, long cumRxBytes, long cumTxBytes, Long bizUsedBytes);
}
