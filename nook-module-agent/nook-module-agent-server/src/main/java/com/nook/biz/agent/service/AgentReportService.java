package com.nook.biz.agent.service;

import com.nook.biz.agent.controller.vo.AgentHeartbeatReqVO;
import com.nook.biz.agent.controller.vo.AgentNicTrafficReqVO;
import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;

import java.util.List;

/**
 * Agent 上报数据 Service 接口
 *
 * @author nook
 */
public interface AgentReportService {

    /**
     * 接收心跳上报
     *
     * @param serverId server 编号
     * @param req      心跳上报
     * @param clientIp 客户端 IP
     */
    void receiveHeartbeat(String serverId, AgentHeartbeatReqVO req, String clientIp);

    /**
     * 接收 NIC 流量上报
     *
     * @param serverId server 编号
     * @param req      NIC 流量上报
     */
    void receiveNicTraffic(String serverId, AgentNicTrafficReqVO req);

    /**
     * 获得本机应存在的全部 xray 客户端期望态
     *
     * @param serverId server 编号
     * @return 期望态列表
     */
    List<XrayReconcileClientDTO> getDesiredClients(String serverId);

    /**
     * 获得落地机应施加的限速
     *
     * @param serverId 落地机 server 编号
     * @return 限速 Mbps (0 = 不限)
     */
    int getLandingDesiredBandwidthMbps(String serverId);

    /**
     * 落地机 socks5 端口 (agent 建 nft 业务流量计数器用)
     *
     * @param serverId 落地机 server 编号
     * @return socks5 端口; 0 = 未配置
     */
    int getLandingSocks5Port(String serverId);
}
