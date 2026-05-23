package com.nook.biz.agent.controller.admin;

import com.nook.biz.agent.api.enums.AgentHostType;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.agent.service.AgentInstallScriptService;
import com.nook.biz.node.api.resource.ResourceIpPoolApi;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerCredentialApi;
import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
import com.nook.framework.web.WebStreamingProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;

/**
 * 管理后台 - Agent 装机 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/agent")
@RequiredArgsConstructor
@Validated
public class AgentInstallController {

    /** Landing 装机 SCP 上传 + apt + binary 下载默认窗口; ip_pool 表没存 timeout 字段时 emitter 用此值. */
    private static final int LANDING_INSTALL_TIMEOUT_DEFAULT_SECONDS = 600;

    private final AgentInstallScriptService agentInstallScriptService;
    private final ResourceServerApi resourceServerApi;
    private final ResourceServerCredentialApi resourceServerCredentialApi;
    private final ResourceIpPoolApi resourceIpPoolApi;
    private final StreamingEndpointSupport streamingSupport;
    private final WebStreamingProperties webStreamingProperties;

    /**
     * SSH 自动装 nook-agent (流式日志走 ResponseBodyEmitter); hostType=SERVER 走 resource_server, IP_POOL 走 resource_ip_pool.
     *
     * @param id    host 主键 (server id 或 ip_pool id)
     * @param reqVO 装机表单 (含 hostType + 路径 / URL / 各种 timeout / xray 信息等)
     * @return ResponseBodyEmitter 流式回写 SSH 脚本 stdout
     */
    @PostMapping(value = "/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter install(@RequestParam("id") String id,
                                       @Valid @RequestBody AgentInstallReqVO reqVO) {
        AgentHostType hostType = reqVO.getHostType() != null ? reqVO.getHostType() : AgentHostType.SERVER;
        reqVO.setHostType(hostType);
        int installTimeoutSec;
        if (hostType == AgentHostType.SERVER) {
            resourceServerApi.validateExists(id);
            ResourceServerCredentialRespDTO cred = resourceServerCredentialApi.requireByServerId(id);
            installTimeoutSec = cred.getInstallTimeoutSeconds();
        } else {
            resourceIpPoolApi.validateExists(id);
            installTimeoutSec = reqVO.getInstallTimeoutSeconds() != null
                    ? reqVO.getInstallTimeoutSeconds() : LANDING_INSTALL_TIMEOUT_DEFAULT_SECONDS;
        }
        Duration emitterTimeout = Duration.ofSeconds(installTimeoutSec)
                .plus(webStreamingProperties.getEmitterBuffer());
        return streamingSupport.stream("agent-install:" + id, emitterTimeout,
                lineSink -> agentInstallScriptService.installStreaming(id, reqVO, lineSink));
    }

    /**
     * 装机元信息 (backend 已知数据), 前端 ProvisionDialog 表单 prefill 用.
     *
     * @param role     frontline / landing (默认 frontline)
     * @param hostType SERVER / IP_POOL; 可空, frontline 默认 SERVER, landing 默认 IP_POOL
     * @param hostId   server id 或 ip_pool id; 可空
     * @return meta
     */
    @GetMapping("/install-meta")
    public Result<AgentInstallMetaRespVO> installMeta(
            @RequestParam(value = "role", defaultValue = AgentRole.Codes.FRONTLINE) String role,
            @RequestParam(value = "hostType", required = false) AgentHostType hostType,
            @RequestParam(value = "hostId", required = false) String hostId,
            @RequestParam(value = "serverId", required = false) String serverId) {
        String resolvedHostId = hostId != null ? hostId : serverId;
        return Result.ok(agentInstallScriptService.getInstallMeta(role, hostType, resolvedHostId));
    }
}
