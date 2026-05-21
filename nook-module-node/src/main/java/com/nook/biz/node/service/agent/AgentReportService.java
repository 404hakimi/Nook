package com.nook.biz.node.service.agent;

import com.nook.biz.node.controller.agent.vo.AgentHeartbeatReqVO;
import com.nook.biz.node.controller.agent.vo.AgentNicTrafficReqVO;
import com.nook.biz.node.controller.agent.vo.AgentTaskResultReqVO;
import com.nook.biz.node.controller.agent.vo.AgentTaskRespVO;
import com.nook.biz.node.controller.agent.vo.AgentXrayTrafficReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;

import java.util.List;

/** Agent push 数据接收 + 任务队列对接. */
public interface AgentReportService {

    /** 接收心跳; 更新 runtime + 清 consecutive_miss / temp_unhealthy. */
    void receiveHeartbeat(ResourceServerDO server, AgentHeartbeatReqVO req, String clientIp);

    /** 接收 NIC 流量字节数并写 resource_server_capacity.used_traffic_bytes. */
    void receiveNicTraffic(ResourceServerDO server, AgentNicTrafficReqVO req);

    /** Agent 轮询拉 PENDING 任务, 同时把状态改 PICKED. */
    List<AgentTaskRespVO> pullPendingTasks(ResourceServerDO server, int limit);

    /** Agent 上报任务执行结果. */
    void receiveTaskResult(ResourceServerDO server, AgentTaskResultReqVO req);

    /** Agent 上报 xray user 流量累计值, 转交 XrayClientTrafficSampleService.applyAgentStats. */
    void receiveXrayTraffic(ResourceServerDO server, AgentXrayTrafficReqVO req);
}
