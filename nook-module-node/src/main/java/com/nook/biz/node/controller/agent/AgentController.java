package com.nook.biz.node.controller.agent;

import com.nook.biz.node.controller.agent.vo.AgentHeartbeatReqVO;
import com.nook.biz.node.controller.agent.vo.AgentNicTrafficReqVO;
import com.nook.biz.node.controller.agent.vo.AgentTaskResultReqVO;
import com.nook.biz.node.controller.agent.vo.AgentTaskRespVO;
import com.nook.biz.node.controller.agent.vo.AgentXrayTrafficReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.agent.AgentAuthService;
import com.nook.biz.node.service.agent.AgentReportService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent push / pull 接口; 走 X-Agent-Token Header 鉴权, 跟 admin sa-token 体系隔离.
 *
 * <p>路径前缀 /api/agent 不在 SaTokenConfig 拦截清单 (现配 /admin/** + /portal/**), 默认放行;
 * 鉴权由 AgentAuthService 在每个方法入口校验 token.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Validated
public class AgentController {

    private static final String TOKEN_HEADER = "X-Agent-Token";

    private final AgentAuthService agentAuthService;
    private final AgentReportService agentReportService;

    /** 心跳 (每 1min). */
    @PostMapping("/heartbeat")
    public Result<Boolean> heartbeat(@RequestHeader(TOKEN_HEADER) String token,
                                     @RequestBody @Valid AgentHeartbeatReqVO reqVO,
                                     HttpServletRequest httpReq) {
        ResourceServerDO srv = agentAuthService.verifyAndGetServer(token);
        agentReportService.receiveHeartbeat(srv, reqVO, ClientIpResolver.resolve(httpReq));
        return Result.ok(true);
    }

    /** 机房 NIC 流量上报 (每 5min, vnstat). */
    @PostMapping("/nic-traffic")
    public Result<Boolean> nicTraffic(@RequestHeader(TOKEN_HEADER) String token,
                                      @RequestBody @Valid AgentNicTrafficReqVO reqVO) {
        ResourceServerDO srv = agentAuthService.verifyAndGetServer(token);
        agentReportService.receiveNicTraffic(srv, reqVO);
        return Result.ok(true);
    }

    /** Agent 拉 PENDING 任务 (每 30s 轮询); 同步标 PICKED. */
    @GetMapping("/tasks")
    public Result<List<AgentTaskRespVO>> pullTasks(@RequestHeader(TOKEN_HEADER) String token,
                                                   @RequestParam(value = "limit", defaultValue = "10") int limit) {
        ResourceServerDO srv = agentAuthService.verifyAndGetServer(token);
        return Result.ok(agentReportService.pullPendingTasks(srv, limit));
    }

    /** Agent 上报任务执行结果. */
    @PostMapping("/task-result")
    public Result<Boolean> taskResult(@RequestHeader(TOKEN_HEADER) String token,
                                      @RequestBody @Valid AgentTaskResultReqVO reqVO) {
        ResourceServerDO srv = agentAuthService.verifyAndGetServer(token);
        agentReportService.receiveTaskResult(srv, reqVO);
        return Result.ok(true);
    }

    /** Agent 上报 xray user 累计流量 (每 5min, statsquery). */
    @PostMapping("/xray-traffic")
    public Result<Boolean> xrayTraffic(@RequestHeader(TOKEN_HEADER) String token,
                                       @RequestBody @Valid AgentXrayTrafficReqVO reqVO) {
        ResourceServerDO srv = agentAuthService.verifyAndGetServer(token);
        agentReportService.receiveXrayTraffic(srv, reqVO);
        return Result.ok(true);
    }
}
