package com.nook.biz.agent.service;

import com.nook.biz.agent.controller.vo.AgentHeartbeatReqVO;
import com.nook.biz.agent.controller.vo.AgentNicTrafficReqVO;
import com.nook.biz.agent.controller.vo.AgentTaskResultReqVO;
import com.nook.biz.agent.controller.vo.AgentTaskRespVO;

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
     * 拉取 Agent 待执行任务
     *
     * @param serverId server 编号
     * @param limit    拉取上限
     * @return 任务列表
     */
    List<AgentTaskRespVO> pullPendingTasks(String serverId, int limit);

    /**
     * 接收任务执行结果
     *
     * @param serverId server 编号
     * @param req      任务结果
     */
    void receiveTaskResult(String serverId, AgentTaskResultReqVO req);
}
