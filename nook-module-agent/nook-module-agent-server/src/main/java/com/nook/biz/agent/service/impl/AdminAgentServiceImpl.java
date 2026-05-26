package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.agent.convert.AdminAgentConvert;
import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.agent.framework.binary.AgentBinaryResolver;
import com.nook.biz.agent.framework.config.AgentProperties;
import com.nook.biz.agent.service.AdminAgentService;
import com.nook.biz.agent.service.AgentTaskDispatchService;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerRuntimeApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Admin Agent 管理 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class AdminAgentServiceImpl implements AdminAgentService {

    private final ResourceServerApi resourceServerApi;
    private final ResourceServerRuntimeApi resourceServerRuntimeApi;
    private final AgentTaskDispatchService agentTaskDispatchService;
    private final AgentBinaryResolver agentBinaryResolver;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentProperties agentProperties;

    @Override
    public AdminAgentDetailRespVO detail(String serverId) {
        ResourceServerRespDTO s = resourceServerApi.validateExists(serverId);
        ResourceServerRuntimeRespDTO rt = resourceServerRuntimeApi.getByServerId(serverId);
        return AdminAgentConvert.INSTANCE.toDetail(s, rt, LocalDateTime.now());
    }

    @Override
    public String dispatchUpgrade(String serverId) {
        resourceServerApi.validateExists(serverId);
        if (StrUtil.isBlank(agentProperties.getBackendPublicUrl())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "nook.agent.backend-public-url 未配置, agent 无法回拉 binary");
        }
        ResourceServerRuntimeRespDTO rt = resourceServerRuntimeApi.getByServerId(serverId);
        String role = AgentRole.extractCodeFromAgentVersion(rt == null ? null : rt.getAgentVersion());
        AgentBinaryResolver.AgentBinary bin = agentBinaryResolver.resolve(role, "linux", "amd64");
        String url = agentProperties.getBackendPublicUrl() + "/admin/agent-dist/bin?role=" + role;
        String fullVersion = role + "-" + bin.version();
        String payload = String.format("{\"url\":\"%s\",\"sha256\":\"%s\",\"version\":\"%s\"}",
                escape(url), escape(bin.sha256()), escape(fullVersion));
        // upgrade 走 agent_type = role (从已装 agent_version 提取的 frontline/landing)
        return agentTaskDispatchService.dispatch(AgentRole.fromCode(role), serverId, AgentTaskType.AGENT_UPGRADE.getCode(), payload);
    }

    @Override
    public PageResult<AgentTaskDO> pageTasks(String serverId, AdminAgentTaskPageReqVO reqVO) {
        resourceServerApi.validateExists(serverId);
        // server detail 只看自己 server 的 agent_runtime 派生的 agentType (默认 frontline)
        ResourceServerRuntimeRespDTO rt = resourceServerRuntimeApi.getByServerId(serverId);
        String agentType = AgentRole.extractCodeFromAgentVersion(rt == null ? null : rt.getAgentVersion());
        IPage<AgentTaskDO> page = agentTaskMapper.selectPageByHost(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                agentType, serverId, reqVO);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
