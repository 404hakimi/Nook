package com.nook.biz.agent.controller.admin;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentPageReqVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskRespVO;
import com.nook.biz.agent.convert.AdminAgentConvert;
import com.nook.biz.agent.convert.AgentTaskConvert;
import com.nook.biz.agent.service.AdminAgentService;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
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

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 管理后台 - Agent 管理 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/agent")
@RequiredArgsConstructor
@Validated
public class AdminAgentController {

    private final AdminAgentService adminAgentService;

    /**
     * Agent 总览分页 (规范 §1 三步走: Convert 提 ids → Service 批量查 aggregates → Convert 拼装 VO).
     *
     * @param reqVO 分页 + 筛选
     * @return 每行含 onlineState / agentVersion / xrayVersion / configSyncState / 流量 等汇总字段
     */
    @GetMapping("/page")
    public Result<PageResult<AdminAgentListItemRespVO>> page(@Valid @ModelAttribute AdminAgentPageReqVO reqVO) {
        PageResult<ResourceServerRespDTO> page = adminAgentService.pageServers(reqVO);
        if (CollUtil.isEmpty(page.getRecords())) {
            return Result.ok(PageResult.empty());
        }
        Set<String> ids = AdminAgentConvert.INSTANCE.extractServerIds(page.getRecords());
        AdminAgentService.ListAggregates agg = adminAgentService.loadListAggregates(ids);
        return Result.ok(PageResult.of(page.getTotal(),
                AdminAgentConvert.INSTANCE.convertList(page.getRecords(), agg, LocalDateTime.now())));
    }

    /**
     * Agent 详情 (单 server).
     *
     * @param serverId server 主键
     * @return 含 capacity / agentToken 末 8 位 / consecutive_miss 等
     */
    @GetMapping("/{serverId}")
    public Result<AdminAgentDetailRespVO> detail(@PathVariable String serverId) {
        return Result.ok(adminAgentService.detail(serverId));
    }

    /**
     * 派 agent_upgrade task; agent 端拉 backend 当前部署的 binary (nook-agent-*-linux-amd64).
     *
     * @param serverId server 主键
     * @return 派出去的 task id
     */
    @PostMapping("/{serverId}/upgrade")
    public Result<String> upgrade(@PathVariable String serverId) {
        return Result.ok(adminAgentService.dispatchUpgrade(serverId));
    }

    /**
     * 某 server 的 agent_task 历史分页 (createdAt 倒序; 支持 taskType / status 可选筛选).
     *
     * @param serverId server 主键
     * @param reqVO    分页 + 筛选参数
     * @return AdminAgentTaskRespVO 分页结果
     */
    @GetMapping("/{serverId}/tasks/page")
    public Result<PageResult<AdminAgentTaskRespVO>> pageTasks(
            @PathVariable String serverId,
            @Valid @ModelAttribute AdminAgentTaskPageReqVO reqVO) {
        return Result.ok(AgentTaskConvert.INSTANCE.convertPage(
                adminAgentService.pageTasks(serverId, reqVO)));
    }
}
