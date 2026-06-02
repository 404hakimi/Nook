package com.nook.biz.agent.controller;

import com.nook.biz.agent.controller.vo.AgentHeartbeatReqVO;
import com.nook.biz.agent.controller.vo.AgentNicTrafficReqVO;
import com.nook.biz.agent.controller.vo.LandingDesiredRespVO;
import com.nook.biz.agent.framework.auth.AuthenticatedAgent;
import com.nook.biz.agent.service.AgentReportService;
import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;
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
     * Agent reconcile 拉本机应存在的全部 xray 客户端期望态 (每 5min + 下单立即推).
     * agent 跟本地实际 diff → adu/rmu/ado/rmo/adrules 收敛。
     *
     * @param serverId 已认证 server id
     * @return 期望态列表 (含预拼 adu/ado/adrules JSON)
     */
    @GetMapping("/reconcile/desired")
    public Result<List<XrayReconcileClientDTO>> reconcileDesired(@AuthenticatedAgent String serverId) {
        return Result.ok(agentReportService.getDesiredClients(serverId));
    }

    /**
     * 落地机拉期望配置 (出口限速 Mbps + socks5 端口); agent 一轮 reconcile 拉一次,
     * 分发给 tc 整形(取套餐带宽与本机上限较小值)和 nft 业务流量计数器维护.
     *
     * @param serverId 已认证 server id (落地机)
     * @return 期望配置 (bandwidthMbps 0=不限; socks5Port 0=未配)
     */
    @GetMapping("/landing/desired")
    public Result<LandingDesiredRespVO> landingDesired(@AuthenticatedAgent String serverId) {
        LandingDesiredRespVO vo = new LandingDesiredRespVO();
        vo.setBandwidthMbps(agentReportService.getLandingDesiredBandwidthMbps(serverId));
        vo.setSocks5Port(agentReportService.getLandingSocks5Port(serverId));
        return Result.ok(vo);
    }
}
