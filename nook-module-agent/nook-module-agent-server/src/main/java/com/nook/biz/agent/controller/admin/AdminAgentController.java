package com.nook.biz.agent.controller.admin;

import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskRespVO;
import com.nook.biz.agent.convert.AgentTaskConvert;
import com.nook.biz.agent.service.AdminAgentService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - Agent 管理 Controller (server 列表分页走 /admin/resource/server-frontline/page-frontline)
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/agent")
@Validated
public class AdminAgentController {

    @Resource
    private AdminAgentService adminAgentService;

    /**
     * 派 agent_upgrade task; agent 端拉 backend 当前部署的 binary (nook-agent-*-linux-amd64).
     *
     * @param serverId server 主键
     * @return 派出去的 task id
     */
    @PostMapping("/upgrade-agent")
    public Result<String> upgrade(@RequestParam("serverId") String serverId) {
        return Result.ok(adminAgentService.dispatchUpgrade(serverId));
    }

    /**
     * 某 server 的 agent_task 历史分页 (createdAt 倒序; 支持 taskType / status 可选筛选).
     *
     * @param serverId server 主键
     * @param reqVO    分页 + 筛选参数
     * @return AdminAgentTaskRespVO 分页结果
     */
    @GetMapping("/page-task")
    public Result<PageResult<AdminAgentTaskRespVO>> pageTasks(
            @RequestParam("serverId") String serverId,
            @Valid @ModelAttribute AdminAgentTaskPageReqVO reqVO) {
        return Result.ok(AgentTaskConvert.INSTANCE.convertPage(
                adminAgentService.pageTasks(serverId, reqVO)));
    }
}
