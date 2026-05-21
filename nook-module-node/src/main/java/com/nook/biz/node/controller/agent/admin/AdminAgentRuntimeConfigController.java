package com.nook.biz.node.controller.agent.admin;

import com.nook.biz.node.controller.agent.admin.vo.AgentRuntimeConfigRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AgentRuntimeConfigSaveReqVO;
import com.nook.biz.node.dal.dataobject.agent.AgentRuntimeConfigDO;
import com.nook.biz.node.service.agent.AgentRuntimeConfigService;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Admin 管理 agent 运行时 yaml; 改后派 config_reload task 给 agent. */
@RestController
@RequestMapping("/admin/agent-runtime-config")
@RequiredArgsConstructor
@Validated
public class AdminAgentRuntimeConfigController {

    private final AgentRuntimeConfigService service;

    @GetMapping("/{serverId}")
    public Result<AgentRuntimeConfigRespVO> get(@PathVariable String serverId) {
        AgentRuntimeConfigDO row = service.get(serverId);
        AgentRuntimeConfigRespVO vo = new AgentRuntimeConfigRespVO();
        vo.setServerId(serverId);
        if (row == null) {
            vo.setSyncState("NEVER_CONFIGURED");
            return Result.ok(vo);
        }
        vo.setConfigYaml(row.getConfigYaml());
        vo.setUpdatedAt(row.getUpdatedAt());
        vo.setUpdatedBy(row.getUpdatedBy());
        vo.setAppliedAt(row.getAppliedAt());
        vo.setAppliedYamlMd5(row.getAppliedYamlMd5());
        String storedMd5 = DigestUtils.md5DigestAsHex(
                row.getConfigYaml().getBytes(StandardCharsets.UTF_8));
        vo.setSyncState(Objects.equals(storedMd5, row.getAppliedYamlMd5()) ? "SYNCED" : "PENDING");
        return Result.ok(vo);
    }

    @PutMapping("/{serverId}")
    public Result<String> save(@PathVariable String serverId,
                               @Valid @RequestBody AgentRuntimeConfigSaveReqVO req) {
        String taskId = service.save(serverId, req.getConfigYaml(), StpSystemUtil.getLoginIdAsString());
        return Result.ok(taskId);
    }
}
