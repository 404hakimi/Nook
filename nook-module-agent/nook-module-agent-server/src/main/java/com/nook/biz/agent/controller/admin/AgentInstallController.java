package com.nook.biz.agent.controller.admin;

import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.agent.service.AgentInstallScriptService;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
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

/** Admin 端 Agent 装机: SSH 流式装机 + 装机元信息查询 (前端 ProvisionDialog prefill 用). 走 admin sa-token 拦截. */
@RestController
@RequestMapping("/admin/agent")
@RequiredArgsConstructor
@Validated
public class AgentInstallController {

    private final AgentInstallScriptService agentInstallScriptService;
    private final ResourceServerApi resourceServerApi;
    private final StreamingEndpointSupport streamingSupport;
    private final WebStreamingProperties webStreamingProperties;

    /**
     * SSH 自动装 nook-agent (流式日志走 ResponseBodyEmitter); 复用 resource_server 已存 SSH 凭据.
     *
     * @param id    server 主键
     * @param reqVO 装机表单 (路径 / URL / 各种 timeout / xray 信息等, frontend 持有默认 + 用户可改)
     * @return ResponseBodyEmitter 流式回写 SSH 脚本 stdout
     */
    @PostMapping(value = "/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter install(@RequestParam("id") String id,
                                       @Valid @RequestBody AgentInstallReqVO reqVO) {
        ResourceServerRespDTO srv = resourceServerApi.validateExists(id);
        Duration emitterTimeout = Duration.ofSeconds(srv.getInstallTimeoutSeconds())
                .plus(webStreamingProperties.getEmitterBuffer());
        return streamingSupport.stream("agent-install:" + id, emitterTimeout,
                lineSink -> agentInstallScriptService.installStreaming(id, reqVO, lineSink));
    }

    /**
     * 装机元信息 (backend 已知数据), 前端 ProvisionDialog 表单 prefill 用; frontline + serverId 时附带 xray 信息.
     *
     * @param role     frontline / landing (默认 frontline)
     * @param serverId 可选; 选了 server 才返 SSH 默认 + xray bin/port
     * @return meta (backendUrl + 可选 xray + 可选 SSH timeouts)
     */
    @GetMapping("/install-meta")
    public Result<AgentInstallMetaRespVO> installMeta(
            @RequestParam(value = "role", defaultValue = AgentRole.Codes.FRONTLINE) String role,
            @RequestParam(value = "serverId", required = false) String serverId) {
        return Result.ok(agentInstallScriptService.getInstallMeta(role, serverId));
    }
}
