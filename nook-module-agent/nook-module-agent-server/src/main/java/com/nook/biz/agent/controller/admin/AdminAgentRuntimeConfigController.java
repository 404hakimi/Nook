package com.nook.biz.agent.controller.admin;

import com.nook.biz.agent.controller.admin.vo.AgentRuntimeConfigRespVO;
import com.nook.biz.agent.controller.admin.vo.AgentRuntimeConfigSaveReqVO;
import com.nook.biz.agent.convert.AgentRuntimeConfigConvert;
import com.nook.biz.agent.service.AgentRuntimeConfigService;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - Agent 运行时配置 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/agent-runtime-config")
@RequiredArgsConstructor
@Validated
public class AdminAgentRuntimeConfigController {

    private final AgentRuntimeConfigService agentRuntimeConfigService;

    /**
     * 取某 server 当前 yaml + 同步状态.
     *
     * @param serverId server 主键
     * @return yaml 内容 + 同步状态 (NEVER_CONFIGURED / SYNCED / PENDING)
     */
    @GetMapping("/{serverId}")
    public Result<AgentRuntimeConfigRespVO> get(@PathVariable String serverId) {
        return Result.ok(AgentRuntimeConfigConvert.INSTANCE.convertDetail(
                serverId, agentRuntimeConfigService.get(serverId)));
    }

    /**
     * 保存 yaml 并派 config_reload task.
     *
     * @param serverId server 主键
     * @param req      含新 yaml
     * @return 派出去的 task id (agent 拉到后 reload, 成功后 backend 回写 applied_md5)
     */
    @PutMapping("/{serverId}")
    public Result<String> save(@PathVariable String serverId,
                               @Valid @RequestBody AgentRuntimeConfigSaveReqVO req) {
        String taskId = agentRuntimeConfigService.save(serverId, req.getConfigYaml(), StpSystemUtil.getLoginIdAsString());
        return Result.ok(taskId);
    }
}
