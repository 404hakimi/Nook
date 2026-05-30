package com.nook.biz.agent.controller.admin;

import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.agent.service.AgentInstallScriptService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 管理后台 - Agent 装机 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/agent")
@Validated
public class AgentInstallController {

    @Resource
    private AgentInstallScriptService agentInstallScriptService;

    /**
     * SSH 自动装 nook-agent (流式日志)
     *
     * @param sourceId 装机源 id (resource_server.id)
     * @param reqVO    装机表单
     * @return 流式响应
     */
    @PostMapping(value = "/install-agent", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter install(@RequestParam("sourceId") String sourceId,
                                       @Valid @RequestBody AgentInstallReqVO reqVO) {
        return agentInstallScriptService.installStream(sourceId, reqVO);
    }

    /**
     * 装机元信息 (前端 prefill 用)
     *
     * @param role     frontline / landing (默认 frontline)
     * @param sourceId resource_server.id; 可空
     * @return meta
     */
    @GetMapping("/get-install-meta")
    public Result<AgentInstallMetaRespVO> installMeta(
            @RequestParam(value = "role", defaultValue = AgentRole.Codes.FRONTLINE) String role,
            @RequestParam(value = "sourceId", required = false) String sourceId) {
        return Result.ok(agentInstallScriptService.getInstallMeta(AgentRole.fromCode(role), sourceId));
    }
}
