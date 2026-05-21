package com.nook.biz.node.controller.agent.admin;

import com.nook.biz.node.controller.agent.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentTaskRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminTruncateLogReqVO;
import com.nook.biz.node.service.agent.AdminAgentService;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 派 truncate_log task: 清指定 server 上的 xray/socks5/agent 日志. */
    @PostMapping("/{serverId}/truncate-log")
    public Result<String> truncateLog(@PathVariable String serverId,
                                      @RequestBody @Valid AdminTruncateLogReqVO reqVO) {
        return Result.ok(adminAgentService.dispatchTruncateLog(serverId, reqVO));
    }

    /** 某 server 最近 N 条 task 历史; UI 任务历史用. limit 默认 20, 上限 200. */
    @GetMapping("/{serverId}/tasks")
    public Result<List<AdminAgentTaskRespVO>> recentTasks(
            @PathVariable String serverId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(adminAgentService.recentTasks(serverId, limit));
    }
}
