package com.nook.biz.agent.service;

import com.nook.biz.agent.controller.vo.AgentHeartbeatReqVO;
import com.nook.biz.agent.controller.vo.AgentNicTrafficReqVO;
import com.nook.biz.agent.controller.vo.AgentTaskResultReqVO;
import com.nook.biz.agent.controller.vo.AgentTaskRespVO;
import com.nook.biz.agent.controller.vo.AgentXrayTrafficReqVO;

import java.util.List;

/** Agent push 数据接收 + 任务队列对接. */
public interface AgentReportService {

    /**
     * 接收心跳, 更新 runtime + 清 consecutive_miss / temp_unhealthy.
     *
     * @param serverId 已认证 server id
     * @param req      心跳上报体 (含 agentVersion 等)
     * @param clientIp HTTP 直连 IP (ClientIpResolver 解析后)
     */
    void receiveHeartbeat(String serverId, AgentHeartbeatReqVO req, String clientIp);

    /**
     * 接收 NIC 流量字节数, 写 resource_server_capacity.used_traffic_bytes.
     *
     * @param serverId 已认证 server id
     * @param req      NIC 流量上报 (rx/tx bytes + period)
     */
    void receiveNicTraffic(String serverId, AgentNicTrafficReqVO req);

    /**
     * Agent 轮询拉 PENDING 任务, 同时 CAS 标 PICKED 防并发重复拾取.
     *
     * @param serverId 已认证 server id
     * @param limit    本次最多拾取条数
     * @return 已 PICKED 的任务列表; 空列表表示当前无任务
     */
    List<AgentTaskRespVO> pullPendingTasks(String serverId, int limit);

    /**
     * Agent 上报任务执行结果, 写 agent_task.status / result_payload;
     * 对 config_reload SUCCESS 顺手回写 agent_runtime_config.applied_md5.
     *
     * @param serverId 已认证 server id
     * @param req      任务结果 (taskId + status + resultPayload)
     */
    void receiveTaskResult(String serverId, AgentTaskResultReqVO req);

    /**
     * Agent 上报 xray user 流量累计值, 转交 XrayClientTrafficSampleService.applyAgentStats.
     *
     * @param serverId 已认证 server id
     * @param req      xray statsquery 快照 (email → up/down bytes)
     */
    void receiveXrayTraffic(String serverId, AgentXrayTrafficReqVO req);
}
