package com.nook.biz.agent.controller;

import com.nook.biz.agent.controller.vo.AgentHeartbeatReqVO;
import com.nook.biz.agent.controller.vo.AgentNicTrafficReqVO;
import com.nook.biz.agent.controller.vo.AgentTaskResultReqVO;
import com.nook.biz.agent.controller.vo.AgentTaskRespVO;
import com.nook.biz.agent.framework.auth.AuthenticatedAgent;
import com.nook.biz.agent.service.AgentReportService;
import com.nook.biz.node.api.xray.XrayClientReconcileApi;
import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;
import com.nook.biz.trade.api.TradeBandwidthApi;
import com.nook.common.web.response.Result;
import com.nook.framework.web.ClientIpResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 上报 / 拉取任务 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/api/agent")
@Validated
public class AgentController {

    @Resource
    private AgentReportService agentReportService;
    @Resource
    private XrayClientReconcileApi xrayClientReconcileApi;
    @Resource
    private TradeBandwidthApi tradeBandwidthApi;

    /**
     * 心跳上报 (每 1min).
     *
     * @param serverId 已认证 server id
     * @param reqVO    心跳上报体
     * @param httpReq  原始 HTTP 请求, 解客户端 IP 用
     * @return 固定 true (失败抛异常由 GlobalExceptionHandler 转 Result)
     */
    @PostMapping("/heartbeat")
    public Result<Boolean> heartbeat(@AuthenticatedAgent String serverId,
                                     @RequestBody @Valid AgentHeartbeatReqVO reqVO,
                                     HttpServletRequest httpReq) {
        agentReportService.receiveHeartbeat(serverId, reqVO, ClientIpResolver.resolve(httpReq));
        return Result.ok(true);
    }

    /**
     * 机房 NIC 流量上报 (每 5min, vnstat).
     *
     * @param serverId 已认证 server id
     * @param reqVO    NIC 流量上报 (rx/tx bytes + period)
     * @return 固定 true
     */
    @PostMapping("/nic-traffic")
    public Result<Boolean> nicTraffic(@AuthenticatedAgent String serverId,
                                      @RequestBody @Valid AgentNicTrafficReqVO reqVO) {
        agentReportService.receiveNicTraffic(serverId, reqVO);
        return Result.ok(true);
    }

    /**
     * Agent 轮询拉 PENDING 任务 (每 30s), 同步 CAS 标 PICKED 防并发重复拾取.
     *
     * @param serverId 已认证 server id
     * @param limit    本次最多拉条数 (默认 10)
     * @return 已 PICKED 的任务列表; 空列表表示当前无任务
     */
    @GetMapping("/tasks")
    public Result<List<AgentTaskRespVO>> pullTasks(@AuthenticatedAgent String serverId,
                                                   @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return Result.ok(agentReportService.pullPendingTasks(serverId, limit));
    }

    /**
     * Agent 上报任务执行结果.
     *
     * @param serverId 已认证 server id
     * @param reqVO    任务结果 (taskId + status + resultPayload)
     * @return 固定 true
     */
    @PostMapping("/task-result")
    public Result<Boolean> taskResult(@AuthenticatedAgent String serverId,
                                      @RequestBody @Valid AgentTaskResultReqVO reqVO) {
        agentReportService.receiveTaskResult(serverId, reqVO);
        return Result.ok(true);
    }

    /**
     * Agent reconcile 拉本机应存在的全部 xray 客户端期望态 (每 5min + 下单立即推).
     * agent 跟本地实际 diff → adu/rmu/ado/rmo/adrules 收敛。
     *
     * @param serverId 已认证 server id
     * @return 期望态列表 (含预拼 adu/ado/adrules JSON)
     */
    @GetMapping("/reconcile/desired")
    public Result<List<XrayReconcileClientDTO>> reconcileDesired(@AuthenticatedAgent String serverId) {
        return Result.ok(xrayClientReconcileApi.getDesiredClients(serverId));
    }

    /**
     * 落地 agent 拉本机应施加的 tc 限速 (Mbps); 落地 1:1, 取占用它的 RUNNING client 的套餐带宽.
     * agent 跟本地 tc qdisc 比对 → 幂等重放 / rate 变更 / 清除。
     *
     * @param serverId 已认证 server id (落地机)
     * @return 限速 Mbps; 0 = 不限 (无客户占用)
     */
    @GetMapping("/landing/desired-bandwidth")
    public Result<Integer> landingDesiredBandwidth(@AuthenticatedAgent String serverId) {
        return Result.ok(tradeBandwidthApi.getLandingDesiredBandwidthMbps(serverId));
    }
}
