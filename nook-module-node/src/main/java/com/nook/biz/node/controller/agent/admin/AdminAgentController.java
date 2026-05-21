package com.nook.biz.node.controller.agent.admin;

import com.nook.biz.node.controller.agent.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentTaskRespVO;
import com.nook.biz.node.convert.agent.AgentTaskConvert;
import com.nook.biz.node.service.agent.AdminAgentService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin 端 Agent 管理: 列表 / 详情 / 升级 / 清日志. 走 admin sa-token 拦截. */
@RestController
@RequestMapping("/admin/agent")
@RequiredArgsConstructor
@Validated
public class AdminAgentController {

    private final AdminAgentService adminAgentService;

    /** Agent 列表 (server + runtime 拼接). */
    @GetMapping("/list")
    public Result<List<AdminAgentListItemRespVO>> list() {
        return Result.ok(adminAgentService.list());
    }

    /** Agent 详情 (含 capacity / agentToken 末 8 位等). */
    @GetMapping("/{serverId}")
    public Result<AdminAgentDetailRespVO> detail(@PathVariable String serverId) {
        return Result.ok(adminAgentService.detail(serverId));
    }

    /** 派 agent_upgrade task; agent 拉 backend 当前部署的 binary (FS 上 nook-agent-*-linux-amd64). */
    @PostMapping("/{serverId}/upgrade")
    public Result<String> upgrade(@PathVariable String serverId) {
        return Result.ok(adminAgentService.dispatchUpgrade(serverId));
    }

    /** 某 server task 历史 分页 (倒序; 含 taskType / status 可选筛选). */
    @GetMapping("/{serverId}/tasks/page")
    public Result<PageResult<AdminAgentTaskRespVO>> pageTasks(
            @PathVariable String serverId,
            @Valid @ModelAttribute AdminAgentTaskPageReqVO reqVO) {
        return Result.ok(AgentTaskConvert.INSTANCE.convertPage(
                adminAgentService.pageTasks(serverId, reqVO)));
    }
}
